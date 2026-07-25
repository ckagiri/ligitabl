package com.ligitabl.api.notification.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.LeaderboardEntry;
import com.ligitabl.model.domain.LeaderboardResponse;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.domain.PhaseType;
import com.ligitabl.model.domain.ResultTeamRank;
import com.ligitabl.model.domain.RoundResult;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.AppSettingRepo;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.LeaderboardRepo;
import com.ligitabl.model.repo.OutboxRepo;
import com.ligitabl.model.repo.RoundResultRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.UserRepo;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoundResultsEmailEnqueuerTest {

    private static final int ROUND = 22;
    private static final RoundSpan SPRINT = span("S8", 21, 23, PhaseType.SPRINT);
    private static final RoundSpan QUARTER = span("Q3", 20, 28, PhaseType.QUARTER);
    private static final RoundSpan FULL_SEASON = span("FS", 1, 38, PhaseType.FULL_SEASON);

    @Mock
    OutboxRepo outboxRepo;

    @Mock
    AppSettingRepo appSettingRepo;

    @Mock
    UserRepo userRepo;

    @Mock
    LeaderboardRepo leaderboardRepo;

    @Mock
    ContestRepo contestRepo;

    @Mock
    CompetitionRepo competitionRepo;

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    RoundResultRepo roundResultRepo;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RoundResultsEmailProperties properties;
    private RoundResultsEmailEnqueuer enqueuer;

    private final UUID seasonId = UUID.randomUUID();
    private final UUID competitionId = UUID.randomUUID();
    private final UUID contestId = UUID.randomUUID();

    private Season season;

    private final TestUser testAccount = new TestUser("test@x.com", "Test Account", true, false);
    private final TestUser alice = new TestUser("alice@x.com", "Alice", true, false);
    private final TestUser unverified = new TestUser("unverified@x.com", "Unverified", false, false);
    private final TestUser optedOut = new TestUser("optout@x.com", "Opted Out", true, true);
    private final TestUser bob = new TestUser("bob@x.com", "Bob", true, false);
    private final TestUser carol = new TestUser("carol@x.com", "Carol", true, false);

    private static int pubSeq = 0;

    private static final class TestUser {
        final UUID id = UUID.randomUUID();
        final String publicId;
        final User user;
        RoundResult result;

        TestUser(String email, String displayName, boolean verified, boolean optedOut) {
            this.publicId = "aaaaaaaa" + "bcdefghjkm".charAt(pubSeq / 10) + "bcdefghjkm".charAt(pubSeq % 10);
            pubSeq++;
            this.user = User.builder()
                    .id(id)
                    .publicId(PublicId.create(publicId))
                    .email(Email.create(email))
                    .displayName(displayName)
                    .emailVerified(verified)
                    .resultsEmailOptOut(optedOut)
                    .roles(java.util.Set.of())
                    .build();
        }

        void withResult(int score, List<Integer> hits) {
            this.result = RoundResult.builder()
                    .roundSubmissionId(UUID.randomUUID())
                    .totalScore(score)
                    .rankings(hits.stream()
                            .map(hit -> ResultTeamRank.builder()
                                    .ranking(TeamRank.of("ARS", 1))
                                    .standingsPosition(1)
                                    .hit(hit)
                                    .build())
                            .toList())
                    .build();
        }
    }

    @BeforeEach
    void setup() {
        properties = new RoundResultsEmailProperties();
        properties.setTopN(2);
        properties.setMode("live");
        enqueuer = new RoundResultsEmailEnqueuer(
                outboxRepo,
                appSettingRepo,
                userRepo,
                leaderboardRepo,
                contestRepo,
                competitionRepo,
                seasonRepo,
                roundResultRepo,
                objectMapper,
                properties);

        season = Season.builder()
                .id(seasonId)
                .competitionId(competitionId)
                .mainContestId(contestId)
                .maxRounds(38)
                .build();

        Competition competition = Competition.builder()
                .id(competitionId)
                .phases(List.of(FULL_SEASON, QUARTER, SPRINT))
                .build();
        Contest contest =
                Contest.builder().id(contestId).seasonId(seasonId).name("Main").build();

        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));
        when(appSettingRepo.findValue(RoundResultsEmailEnqueuer.IGNORE_LIST_SETTING_KEY))
                .thenReturn(Optional.of(" Test@X.com , "));
        when(outboxRepo.save(any())).thenReturn(true);

        testAccount.withResult(180, List.of(0, 0, 0, 0));
        alice.withResult(175, List.of(0, 1, 3, 7));
        unverified.withResult(170, List.of(1, 1, 1, 1));
        optedOut.withResult(165, List.of(1, 1, 1, 1));
        bob.withResult(150, List.of(2, 2, 4, 5));
        carol.withResult(140, List.of(3, 3, 3, 3));

        for (TestUser u : List.of(testAccount, alice, unverified, optedOut, bob, carol)) {
            when(userRepo.findByPublicId(PublicId.create(u.publicId))).thenReturn(Optional.of(u.user));
            when(roundResultRepo.findByUserAndRound(u.id, ROUND)).thenReturn(Optional.of(u.result));
        }

        // Sprint board (fromRound = sprint start): governs recipient selection/order, and its
        // movement/maxScore feed the sprint best-callout directly (no extra query needed).
        stubBoard(
                SPRINT.getFrom(),
                120,
                entry(1, testAccount, 180, 2),
                entry(2, alice, 175, 1),
                entry(3, unverified, 170, 0),
                entry(4, optedOut, 165, 0),
                entry(5, bob, 160, -1),
                entry(6, carol, 140, 0));
        // Quarter board (its own best-callout, looked up separately) and full-season board.
        stubBoard(
                QUARTER.getFrom(),
                130,
                entry(3, testAccount, 500, 0),
                entry(5, alice, 175, 1),
                entry(12, bob, 400, -1));
        stubBoard(
                FULL_SEASON.getFrom(),
                140,
                entry(4, testAccount, 2000, 0),
                entry(18, alice, 1800, 0),
                entry(25, bob, 1500, 0));
    }

    private static RoundSpan span(String code, int from, int to, PhaseType type) {
        return RoundSpan.builder()
                .code(code)
                .name(code)
                .from(from)
                .to(to)
                .type(type)
                .build();
    }

    private LeaderboardEntry entry(int position, TestUser user, int maxScore, int movement) {
        return new LeaderboardEntry(
                position, user.publicId, user.user.getDisplayName(), 0, 0, maxScore, 0, 0, 1, movement, true, false);
    }

    private void stubBoard(int fromRound, int totalParticipants, LeaderboardEntry... entries) {
        when(leaderboardRepo.computeLeaderboard(
                        eq(contestId), eq(seasonId), eq(fromRound), eq(ROUND), isNull(), eq(0), anyInt(), eq(true)))
                .thenReturn(new LeaderboardResponse(
                        List.of(entries), null, false, 0, totalParticipants, false, false, ROUND));
    }

    private void enqueue() {
        enqueuer.enqueue(new RoundAdvancedPayload(seasonId, ROUND, ROUND));
    }

    private List<OutboxEvent> savedEvents() {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepo, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    private RoundResultsPayload payloadOf(OutboxEvent event) throws Exception {
        return objectMapper.readValue(event.getPayload(), RoundResultsPayload.class);
    }

    @Test
    void selectsTopNInLeaderboardOrderSkippingIgnoredUnverifiedAndOptedOut() throws Exception {
        enqueue();

        List<OutboxEvent> events = savedEvents();
        assertThat(events).hasSize(2);

        // Test account (#1) ignored, unverified (#3) and opted-out (#4) skipped,
        // so the top-2 recipients are Alice (#2) and Bob (#5).
        RoundResultsPayload first = payloadOf(events.get(0));
        RoundResultsPayload second = payloadOf(events.get(1));
        assertThat(first.userEmail()).isEqualTo("alice@x.com");
        assertThat(second.userEmail()).isEqualTo("bob@x.com");

        assertThat(events.get(0).getIdempotencyKey())
                .isEqualTo("round-results:%s:%d:%s".formatted(seasonId, ROUND, alice.id));
        assertThat(events.get(0).getEventType()).isEqualTo(OutboxEventTypes.ROUND_RESULTS);
        assertThat(events.get(0).getAggregateType()).isEqualTo("round");
        assertThat(events.get(0).getAggregateId()).isEqualTo(String.valueOf(ROUND));
    }

    @Test
    void payloadMirrorsBannerDataAndPlacements() throws Exception {
        enqueue();

        RoundResultsPayload alicePayload = payloadOf(savedEvents().get(0));

        assertThat(alicePayload.round()).isEqualTo(ROUND);
        assertThat(alicePayload.score()).isEqualTo(175);
        assertThat(alicePayload.currentRound()).isEqualTo(ROUND);
        assertThat(alicePayload.lastRound()).isEqualTo(38);
        assertThat(alicePayload.userDisplayName()).isEqualTo("Alice");

        assertThat(alicePayload.hitDistribution().perfect()).isEqualTo(1);
        assertThat(alicePayload.hitDistribution().closeCalls()).isEqualTo(1);
        assertThat(alicePayload.hitDistribution().nearMisses()).isEqualTo(1);
        assertThat(alicePayload.hitDistribution().bigMisses()).isEqualTo(1);

        // Sprint carries rank, movement and best.
        var sprint = alicePayload.sprint();
        assertThat(sprint.label()).isEqualTo("S8");
        assertThat(sprint.fromRound()).isEqualTo(21);
        assertThat(sprint.toRound()).isEqualTo(23);
        assertThat(sprint.rank()).isEqualTo(2);
        assertThat(sprint.totalParticipants()).isEqualTo(120);
        assertThat(sprint.movement()).isEqualTo(1);
        assertThat(sprint.sprintBest()).isEqualTo(175);
        // score == sprint best and round is past the sprint's first round
        assertThat(sprint.isNewSprintBest()).isTrue();

        // Quarter also carries rank, movement and best.
        var quarter = alicePayload.quarter();
        assertThat(quarter.label()).isEqualTo("Q3");
        assertThat(quarter.fromRound()).isEqualTo(20);
        assertThat(quarter.toRound()).isEqualTo(28);
        assertThat(quarter.rank()).isEqualTo(5);
        assertThat(quarter.totalParticipants()).isEqualTo(130);
        assertThat(quarter.movement()).isEqualTo(1);
        assertThat(quarter.quarterBest()).isEqualTo(175);
        // score == quarter best and round is past the quarter's first round
        assertThat(quarter.isNewQuarterBest()).isTrue();

        // Season is secondary — rank/total only, no best/movement.
        assertThat(alicePayload.season().label()).isEqualTo("FS");
        assertThat(alicePayload.season().rank()).isEqualTo(18);
        assertThat(alicePayload.season().totalParticipants()).isEqualTo(140);

        RoundResultsPayload bobPayload = payloadOf(savedEvents().get(1));
        // Bob's round score (150) is below both his sprint best (160) and quarter best (400)
        assertThat(bobPayload.sprint().isNewSprintBest()).isFalse();
        assertThat(bobPayload.sprint().movement()).isEqualTo(-1);
        assertThat(bobPayload.quarter().isNewQuarterBest()).isFalse();
        assertThat(bobPayload.quarter().movement()).isEqualTo(-1);
    }

    @Test
    void quarterStartingAtRoundOneSuppressesSeasonNotQuarter() throws Exception {
        // Q1 (gw1-25, containing ROUND=22) starts the same round as the season — even though
        // Q1's own range is shorter than the full 38-round season, only `from` matters for
        // redundancy: while still within Q1, season's cumulative data-so-far is identical to Q1's.
        RoundSpan q1 = span("Q1", 1, 25, PhaseType.QUARTER);
        when(competitionRepo.findById(competitionId))
                .thenReturn(Optional.of(Competition.builder()
                        .id(competitionId)
                        .phases(List.of(FULL_SEASON, q1, SPRINT))
                        .build()));
        // Quarter board now queried from round 1 (the quarter's own fromRound), not round 20.
        when(leaderboardRepo.computeLeaderboard(
                        eq(contestId), eq(seasonId), eq(1), eq(ROUND), isNull(), eq(0), anyInt(), eq(true)))
                .thenReturn(new LeaderboardResponse(
                        List.of(entry(3, testAccount, 500, 0), entry(5, alice, 175, 1), entry(12, bob, 400, -1)),
                        null,
                        false,
                        0,
                        130,
                        false,
                        false,
                        ROUND));

        enqueue();

        RoundResultsPayload alicePayload = payloadOf(savedEvents().get(0));
        assertThat(alicePayload.quarter()).isNotNull();
        assertThat(alicePayload.quarter().label()).isEqualTo("Q1");
        assertThat(alicePayload.season()).isNull();
    }

    @Test
    void quarterStartingAfterRoundOneKeepsBothQuarterAndSeason() throws Exception {
        // Default fixture QUARTER (Q3, gw20-28) starts well after the season's round 1 —
        // no longer redundant with season, so both should be present.
        enqueue();

        RoundResultsPayload alicePayload = payloadOf(savedEvents().get(0));
        assertThat(alicePayload.quarter()).isNotNull();
        assertThat(alicePayload.season()).isNotNull();
    }

    @Test
    void sprintStartingAtQuarterStartSuppressesQuarterOnly() throws Exception {
        // Sprint starts the same round as the quarter (both gw20) — quarter would just
        // repeat this sprint's numbers, so it's dropped. Season stays: the quarter itself
        // (gw20-28) doesn't start at the season's round 1, so it's not redundant with season.
        RoundSpan sprintAtQuarterStart = span("S1", 20, 22, PhaseType.SPRINT);
        when(competitionRepo.findById(competitionId))
                .thenReturn(Optional.of(Competition.builder()
                        .id(competitionId)
                        .phases(List.of(FULL_SEASON, QUARTER, sprintAtQuarterStart))
                        .build()));
        // Recipient selection + sprint's own placement now read from round 20.
        when(leaderboardRepo.computeLeaderboard(
                        eq(contestId), eq(seasonId), eq(20), eq(ROUND), isNull(), eq(0), anyInt(), eq(true)))
                .thenReturn(new LeaderboardResponse(
                        List.of(
                                entry(1, testAccount, 180, 2),
                                entry(2, alice, 175, 1),
                                entry(3, unverified, 170, 0),
                                entry(4, optedOut, 165, 0),
                                entry(5, bob, 160, -1),
                                entry(6, carol, 140, 0)),
                        null,
                        false,
                        0,
                        120,
                        false,
                        false,
                        ROUND));

        enqueue();

        RoundResultsPayload alicePayload = payloadOf(savedEvents().get(0));
        assertThat(alicePayload.sprint()).isNotNull();
        assertThat(alicePayload.quarter()).isNull();
        assertThat(alicePayload.season()).isNotNull();
    }

    @Test
    void testModeSendsOnlyToIgnoreListAccounts() throws Exception {
        properties.setMode("test");

        enqueue();

        List<OutboxEvent> events = savedEvents();
        assertThat(events).hasSize(1);
        assertThat(payloadOf(events.get(0)).userEmail()).isEqualTo("test@x.com");
    }

    @Test
    void skipsEntirelyWhenSeasonNotFound() {
        when(seasonRepo.findById(seasonId)).thenReturn(Optional.empty());

        enqueue();

        verifyNoInteractions(outboxRepo);
        verifyNoInteractions(leaderboardRepo);
    }

    @Test
    void skipsEntirelyWhenSeasonHasNoMainContest() {
        season.setMainContestId(null);

        enqueue();

        verifyNoInteractions(outboxRepo);
        verifyNoInteractions(leaderboardRepo);
    }

    @Test
    void skipsEntirelyWhenNoPhasesConfigured() {
        when(competitionRepo.findById(competitionId))
                .thenReturn(Optional.of(Competition.builder()
                        .id(competitionId)
                        .phases(List.of())
                        .build()));

        enqueue();

        verifyNoInteractions(outboxRepo);
        verifyNoInteractions(leaderboardRepo);
    }

    @Test
    void usersWithoutARoundResultAreSkipped() {
        // Bob has a leaderboard entry but no result for this round
        when(roundResultRepo.findByUserAndRound(bob.id, ROUND)).thenReturn(Optional.empty());
        when(roundResultRepo.findByUserAndRound(carol.id, ROUND)).thenReturn(Optional.empty());

        enqueue();

        List<OutboxEvent> events = savedEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getIdempotencyKey()).contains(alice.id.toString());
    }

    @Test
    void saveFailureForOneUserDoesNotStopOthers() {
        when(outboxRepo.save(any())).thenAnswer(invocation -> {
            OutboxEvent event = invocation.getArgument(0);
            if (event.getIdempotencyKey().contains(alice.id.toString())) {
                throw new RuntimeException("boom");
            }
            return true;
        });

        enqueue();

        // Bob's event is still saved despite Alice's failure
        List<OutboxEvent> events = savedEvents();
        assertThat(events).hasSize(2);
    }

    @Test
    void emptyIgnoreListMeansNoFiltering() throws Exception {
        when(appSettingRepo.findValue(RoundResultsEmailEnqueuer.IGNORE_LIST_SETTING_KEY))
                .thenReturn(Optional.of(""));

        enqueue();

        List<OutboxEvent> events = savedEvents();
        assertThat(events).hasSize(2);
        assertThat(payloadOf(events.get(0)).userEmail()).isEqualTo("test@x.com");
        assertThat(payloadOf(events.get(1)).userEmail()).isEqualTo("alice@x.com");
    }

    @Test
    void neverSavesWhenTestModeAndIgnoreListEmpty() {
        properties.setMode("test");
        when(appSettingRepo.findValue(RoundResultsEmailEnqueuer.IGNORE_LIST_SETTING_KEY))
                .thenReturn(Optional.empty());

        enqueue();

        verify(outboxRepo, never()).save(any());
    }
}
