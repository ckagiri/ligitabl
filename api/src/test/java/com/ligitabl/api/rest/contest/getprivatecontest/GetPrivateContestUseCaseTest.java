package com.ligitabl.api.rest.contest.getprivatecontest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.web.contest.SegmentTreeBuilder;
import com.ligitabl.api.web.contest.shared.ContestSupport;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionPhaseFixtures;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.Entry;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.LeaderboardRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

/**
 * Verifies the split introduced for {@code resolveCurrentRound}: the default-segment lookup
 * (via {@link ContestSupport#findCurrentSprint}) uses the finalization-adjusted effective
 * position, while the live-segment check (feeding {@code activeOnly}) and the segment tree keep
 * using the raw, real-time current round position.
 */
@ExtendWith(MockitoExtension.class)
class GetPrivateContestUseCaseTest {

    @Mock
    ContestRepo contestRepo;

    @Mock
    EntryRepo entryRepo;

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    CompetitionRepo competitionRepo;

    @Mock
    LeaderboardRepo leaderboardRepo;

    @Mock
    RoundRepo roundRepo;

    @Mock
    SegmentTreeBuilder segmentTreeBuilder;

    @Mock
    ContestSupport contestSupport;

    private GetPrivateContestUseCase useCase;

    private UUID userId;
    private UUID contestId;
    private UUID seasonId;
    private UUID competitionId;
    private UUID currentRoundId;
    private Contest contest;
    private Season season;
    private Competition competition;

    @BeforeEach
    void setUp() {
        useCase = new GetPrivateContestUseCase(
                contestRepo,
                entryRepo,
                seasonRepo,
                competitionRepo,
                leaderboardRepo,
                roundRepo,
                segmentTreeBuilder,
                contestSupport);

        userId = UUID.randomUUID();
        contestId = UUID.randomUUID();
        seasonId = UUID.randomUUID();
        competitionId = UUID.randomUUID();
        currentRoundId = UUID.randomUUID();

        contest = Contest.builder()
                .id(contestId)
                .seasonId(seasonId)
                .name("Office Rivals")
                .isPrivate(true)
                .isOpen(true)
                .joinCode("CODE1")
                .fromRoundPosition(1)
                .toRoundPosition(38)
                .build();

        season = Season.builder()
                .id(seasonId)
                .competitionId(competitionId)
                .name("2025/26")
                .slug(SeasonSlug.of("2025-26"))
                .clientId(1)
                .maxRounds(38)
                .currentRoundId(currentRoundId)
                .build();

        competition = Competition.builder()
                .id(competitionId)
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("PL")
                .phases(CompetitionPhaseFixtures.phases())
                .build();

        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));
        when(entryRepo.findByUserAndContest(userId, contestId))
                .thenReturn(Optional.of(Entry.builder()
                        .userId(userId)
                        .contestId(contestId)
                        .joinedAtRound(1)
                        .build()));
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));
        when(entryRepo.findByContestId(contestId)).thenReturn(List.of());
        when(contestSupport.isOpenForJoining(contest, season, competition)).thenReturn(true);
        org.mockito.Mockito.lenient()
                .when(contestSupport.findCurrentSprint(any(), any(), anyInt()))
                .thenReturn(CompetitionPhaseFixtures.s("S3"));
    }

    private void stubCurrentRound(int position, boolean finalized) {
        when(roundRepo.findById(currentRoundId))
                .thenReturn(Optional.of(Round.builder()
                        .id(currentRoundId)
                        .position(position)
                        .finalized(finalized)
                        .build()));
    }

    @Test
    void noSegmentCode_currentRoundFinalized_defaultSegmentUsesRawPosition() {
        stubCurrentRound(12, true);

        var result = useCase.execute(new GetPrivateContestQuery(contestId, userId, null, 0));

        assertThat(result.isRight()).isTrue();

        verify(contestSupport).findCurrentSprint(contest, competition, 12);

        ArgumentCaptor<Integer> segmentTreePosition = ArgumentCaptor.forClass(Integer.class);
        verify(segmentTreeBuilder).build(eq(contest), eq(competition), eq(userId), segmentTreePosition.capture());
        assertThat(segmentTreePosition.getValue()).isEqualTo(12);
    }

    @Test
    void noSegmentCode_currentRoundNotFinalized_defaultSegmentStepsBackOnePosition() {
        stubCurrentRound(12, false);

        var result = useCase.execute(new GetPrivateContestQuery(contestId, userId, null, 0));

        assertThat(result.isRight()).isTrue();

        // Default-segment lookup uses the finalization-adjusted effective position (12 -> 11).
        verify(contestSupport).findCurrentSprint(contest, competition, 11);

        // Segment tree keeps the raw, real-time current position — unaffected by the adjustment.
        ArgumentCaptor<Integer> segmentTreePosition = ArgumentCaptor.forClass(Integer.class);
        verify(segmentTreeBuilder).build(eq(contest), eq(competition), eq(userId), segmentTreePosition.capture());
        assertThat(segmentTreePosition.getValue()).isEqualTo(12);
    }

    @Test
    void noSegmentCode_currentRoundNotFinalized_activeOnlyStillDerivedFromRawPosition() {
        // S3 = 10-14, so raw position 12 is "live" within it; the effective position (11) would
        // also happen to fall inside S3 here, so pick a returned segment where only the raw
        // position keeps it "live" to prove activeOnly isn't computed from the effective position.
        stubCurrentRound(10, false);
        when(contestSupport.findCurrentSprint(any(), any(), anyInt())).thenReturn(CompetitionPhaseFixtures.s("S2"));

        var result = useCase.execute(new GetPrivateContestQuery(contestId, userId, null, 0));

        assertThat(result.isRight()).isTrue();

        // S2 = 5-9: raw position 10 is NOT live for S2 (activeOnly should be false), even though
        // the effective position used for segment *selection* was 9 (which IS inside S2).
        ArgumentCaptor<Boolean> activeOnly = ArgumentCaptor.forClass(Boolean.class);
        verify(leaderboardRepo)
                .computeLeaderboard(
                        eq(contestId), eq(seasonId), eq(5), eq(9), eq(userId), eq(0), eq(10), activeOnly.capture());
        assertThat(activeOnly.getValue()).isFalse();
    }

    @Test
    void explicitSegmentCode_doesNotConsultDefaultSegmentResolution() {
        stubCurrentRound(12, false);

        var result = useCase.execute(new GetPrivateContestQuery(contestId, userId, "S1", 0));

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().selectedSegment()).isEqualTo(CompetitionPhaseFixtures.s("S1"));

        verify(contestSupport, org.mockito.Mockito.never()).findCurrentSprint(any(), any(), anyInt());
    }
}
