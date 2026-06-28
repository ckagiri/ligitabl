package com.ligitabl.api.web.contest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.LeaderboardRepo;
import com.ligitabl.model.repo.MatchRepo;

@ExtendWith(MockitoExtension.class)
class SegmentTreeBuilderTest {

    @Mock MatchRepo matchRepo;
    @Mock LeaderboardRepo leaderboardRepo;

    private SegmentTreeBuilder builder;

    private UUID seasonId;
    private UUID contestId;
    private UUID userId;

    // Phases: Q1=S1(1-5)+S2(6-10), Q2=S3(11-15)+S4(16-20)
    private List<RoundSpan> phases;
    private Competition competition;

    @BeforeEach
    void setUp() {
        builder = new SegmentTreeBuilder(matchRepo, leaderboardRepo);
        seasonId = UUID.randomUUID();
        contestId = UUID.randomUUID();
        userId = UUID.randomUUID();

        phases = List.of(
                RoundSpan.builder().code("S1").name("Sprint 1").type(PhaseType.SPRINT).from(1).to(5).build(),
                RoundSpan.builder().code("S2").name("Sprint 2").type(PhaseType.SPRINT).from(6).to(10).build(),
                RoundSpan.builder().code("Q1").name("Quarter 1").type(PhaseType.QUARTER).from(1).to(10).build(),
                RoundSpan.builder().code("S3").name("Sprint 3").type(PhaseType.SPRINT).from(11).to(15).build(),
                RoundSpan.builder().code("S4").name("Sprint 4").type(PhaseType.SPRINT).from(16).to(20).build(),
                RoundSpan.builder().code("Q2").name("Quarter 2").type(PhaseType.QUARTER).from(11).to(20).build());

        competition = Competition.builder()
                .id(UUID.randomUUID()).name("Premier League")
                .slug(CompetitionSlug.of("premier-league")).code("PL")
                .phases(phases)
                .build();

        when(matchRepo.groupRoundDateRangesBySeason(any())).thenReturn(Map.of());
    }

    // ─── Tree shape ────────────────────────────────────────────────────────────

    @Test
    void singleSprintWindow_rootIsSprint_noChildren() {
        Contest contest = contest(16, 20); // S4 only
        stubNoRanks();

        List<SegmentNodeDto> tree = builder.build(contest, competition, userId, 18);

        assertThat(tree).hasSize(1);
        SegmentNodeDto root = tree.get(0);
        assertThat(root.id()).isEqualTo("S4");
        assertThat(root.label()).isEqualTo("Sprint 4");
        assertThat(root.children()).isEmpty();
    }

    @Test
    void singleQuarterWindow_rootIsQuarter_sprintsAreChildren() {
        Contest contest = contest(1, 10); // Q1 = S1+S2
        stubNoRanks();

        List<SegmentNodeDto> tree = builder.build(contest, competition, userId, 3);

        assertThat(tree).hasSize(1);
        SegmentNodeDto root = tree.get(0);
        assertThat(root.id()).isEqualTo("Q1");
        assertThat(root.label()).isEqualTo("Quarter 1");
        assertThat(root.children()).hasSize(2);
        assertThat(root.children().get(0).id()).isEqualTo("S1");
        assertThat(root.children().get(1).id()).isEqualTo("S2");
    }

    @Test
    void singleQuarterWindow_doesNotWrapInOverallNode() {
        Contest contest = contest(1, 10); // Q1 only
        stubNoRanks();

        List<SegmentNodeDto> tree = builder.build(contest, competition, userId, 3);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).id()).isNotEqualTo("overall");
    }

    @Test
    void multiQuarterWindow_rootIsOverall_quartersAndSprintsNested() {
        Contest contest = contest(1, 20); // Q1 + Q2
        stubNoRanks();

        List<SegmentNodeDto> tree = builder.build(contest, competition, userId, 3);

        assertThat(tree).hasSize(1);
        SegmentNodeDto root = tree.get(0);
        assertThat(root.id()).isEqualTo("overall");
        assertThat(root.label()).isEqualTo("Overall");
        assertThat(root.children()).hasSize(2);

        SegmentNodeDto q1 = root.children().get(0);
        assertThat(q1.id()).isEqualTo("Q1");
        assertThat(q1.children()).hasSize(2);

        SegmentNodeDto q2 = root.children().get(1);
        assertThat(q2.id()).isEqualTo("Q2");
        assertThat(q2.children()).hasSize(2);
    }

    // ─── Status derivation ────────────────────────────────────────────────────

    @Test
    void statusDerivation_liveFinishedNextFuture() {
        Contest contest = contest(1, 20);
        stubNoRanks();

        // Current round = 6 (inside S2, Q1 LIVE; S1 FINISHED; S3 NEXT; S4 FUTURE)
        List<SegmentNodeDto> tree = builder.build(contest, competition, userId, 6);

        SegmentNodeDto overall = tree.get(0);
        assertThat(overall.status()).isEqualTo("LIVE");

        SegmentNodeDto q1 = overall.children().get(0);
        assertThat(q1.status()).isEqualTo("LIVE");
        assertThat(q1.children().get(0).status()).isEqualTo("FINISHED"); // S1: 1-5 < 6
        assertThat(q1.children().get(1).status()).isEqualTo("LIVE");     // S2: 6-10 contains 6

        SegmentNodeDto q2 = overall.children().get(1);
        assertThat(q2.status()).isEqualTo("FUTURE");
        assertThat(q2.children().get(0).status()).isEqualTo("NEXT");     // S3: 11-15, 11 == 6+1? no, 6+1=7 ≠ 11, so FUTURE
        assertThat(q2.children().get(1).status()).isEqualTo("FUTURE");   // S4: 16-20
    }

    @Test
    void statusNext_sprintStartsAtCurrentPlusOne() {
        // Current position = 10 (inside S2), S3 starts at 11 = current + 1 → NEXT
        Contest contest = contest(1, 20);
        stubNoRanks();

        List<SegmentNodeDto> tree = builder.build(contest, competition, userId, 10);

        SegmentNodeDto q2 = tree.get(0).children().get(1);
        assertThat(q2.children().get(0).status()).isEqualTo("NEXT"); // S3 starts at 11
    }

    // ─── Rank computation ─────────────────────────────────────────────────────

    @Test
    void singleSprintWindow_liveNode_exactlyOneRankCall() {
        Contest contest = contest(16, 20); // S4 only
        stubRank(5);

        builder.build(contest, competition, userId, 18); // current inside S4 → LIVE

        verify(leaderboardRepo, times(1)).computeLeaderboard(
                eq(contestId), eq(seasonId), anyInt(), anyInt(), eq(userId), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void singleSprintWindow_finishedNode_noRankCall() {
        Contest contest = contest(1, 5); // S1 only, current round is 6 (past S1)
        stubNoRanks();

        builder.build(contest, competition, userId, 6); // S1 is FINISHED

        verify(leaderboardRepo, never()).computeLeaderboard(any(), any(), anyInt(), anyInt(), any(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void singleQuarterWindow_liveNodes_atMostTwoRankCalls() {
        Contest contest = contest(1, 10); // Q1 = S1+S2, current inside S1
        stubRank(3);

        builder.build(contest, competition, userId, 3);

        // Quarter root (LIVE) + S1 sprint (LIVE) = 2 calls max
        verify(leaderboardRepo, atMost(2)).computeLeaderboard(any(), any(), anyInt(), anyInt(), any(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void multiQuarterWindow_liveNodes_atMostThreeRankCalls() {
        Contest contest = contest(1, 20); // Q1+Q2, current inside S1
        stubRank(7);

        builder.build(contest, competition, userId, 3);

        // Overall root (LIVE) + Q1 quarter (LIVE) + S1 sprint (LIVE) = 3 calls max
        verify(leaderboardRepo, atMost(3)).computeLeaderboard(any(), any(), anyInt(), anyInt(), any(), anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void rankBelowOrEqualTen_isTopTenTrue() {
        Contest contest = contest(16, 20);
        stubRank(10);

        List<SegmentNodeDto> tree = builder.build(contest, competition, userId, 18);

        assertThat(tree.get(0).isTopTen()).isTrue();
    }

    @Test
    void rankAboveTen_isTopTenFalse() {
        Contest contest = contest(16, 20);
        stubRank(11);

        List<SegmentNodeDto> tree = builder.build(contest, competition, userId, 18);

        assertThat(tree.get(0).isTopTen()).isFalse();
    }

    // ─── Edge cases ───────────────────────────────────────────────────────────

    @Test
    void nullPhases_returnsEmptyTree() {
        Competition noPhases = Competition.builder()
                .id(UUID.randomUUID()).name("Bare").slug(CompetitionSlug.of("bare")).code("B").build();
        Contest contest = contest(1, 20);

        List<SegmentNodeDto> tree = builder.build(contest, noPhases, userId, 5);

        assertThat(tree).isEmpty();
    }

    @Test
    void nullCompetition_returnsEmptyTree() {
        Contest contest = contest(1, 20);

        List<SegmentNodeDto> tree = builder.build(contest, null, userId, 5);

        assertThat(tree).isEmpty();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Contest contest(int from, int to) {
        return Contest.builder()
                .id(contestId).seasonId(seasonId).name("C")
                .fromRoundPosition(from).toRoundPosition(to).build();
    }

    private void stubNoRanks() {
        // Don't configure leaderboardRepo — verify it's never called where needed
        // or let it return null by default (Mockito returns null for unstubbed calls)
    }

    private void stubRank(int rank) {
        LeaderboardEntry mockEntry = mock(LeaderboardEntry.class);
        when(mockEntry.position()).thenReturn(rank);
        LeaderboardResponse mockResponse = mock(LeaderboardResponse.class);
        when(mockResponse.userEntry()).thenReturn(mockEntry);
        when(leaderboardRepo.computeLeaderboard(any(), any(), anyInt(), anyInt(), any(), anyInt(), anyInt(), anyBoolean()))
                .thenReturn(mockResponse);
    }
}
