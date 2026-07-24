package com.ligitabl.api.rest.round.finalizeround;

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
import com.ligitabl.api.notification.outbox.OutboxEventTypes;
import com.ligitabl.api.notification.outbox.RoundResultsEmailProperties;
import com.ligitabl.api.notification.outbox.RoundResultsPayload;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.LeaderboardEntry;
import com.ligitabl.model.domain.LeaderboardResponse;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.domain.PhaseType;
import com.ligitabl.model.domain.ResultTeamRank;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundResult;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.RoundSubmission;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.AppSettingRepo;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.LeaderboardRepo;
import com.ligitabl.model.repo.OutboxRepo;
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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RoundResultsEmailProperties properties;
    private RoundResultsEmailEnqueuer enqueuer;

    private final UUID seasonId = UUID.randomUUID();
    private final UUID competitionId = UUID.randomUUID();
    private final UUID contestId = UUID.randomUUID();

    private Season season;
    private Round round;

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
        RoundSubmission submission;
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
            UUID submissionId = UUID.randomUUID();
            this.submission = RoundSubmission.builder()
                    .id(submissionId)
                    .userId(id)
                    .roundPosition(ROUND)
                    .rankings(List.of())
                    .build();
            this.result = RoundResult.builder()
                    .roundSubmissionId(submissionId)
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
                objectMapper,
                properties);

        season = Season.builder()
                .id(seasonId)
                .competitionId(competitionId)
                .mainContestId(contestId)
                .maxRounds(38)
                .build();
        round = Round.builder().id(UUID.randomUUID()).position(ROUND).build();

        Competition competition = Competition.builder()
                .id(competitionId)
                .phases(List.of(FULL_SEASON, QUARTER, SPRINT))
                .build();
        Contest contest = Contest.builder()
                .id(contestId)
                .seasonId(seasonId)
                .name("Main")
                .build();

        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));
        when(appSettingRepo.findValue(RoundResultsEmailEnqueuer.IGNORE_LIST_SETTING_KEY))
                .thenReturn(Optional.of(" Test@X.com , "));
        when(outboxRepo.save(any())).thenReturn(true);

        for (TestUser u : List.of(testAccount, alice, unverified, optedOut, bob, carol)) {
            when(userRepo.findByPublicId(PublicId.create(u.publicId))).thenReturn(Optional.of(u.user));
        }

        testAccount.withResult(180, List.of(0, 0, 0, 0));
        alice.withResult(175, List.of(0, 1, 3, 7));
        unverified.withResult(170, List.of(1, 1, 1, 1));
        optedOut.withResult(165, List.of(1, 1, 1, 1));
        bob.withResult(150, List.of(2, 2, 4, 5));
        carol.withResult(140, List.of(3, 3, 3, 3));

        // Sprint board (fromRound = sprint start): ranked order with movement/maxScore
        stubBoard(SPRINT.getFrom(), 120, entry(1, testAccount, 180, 2), entry(2, alice, 175, 1), entry(3, unverified, 170, 0), entry(4, optedOut, 165, 0), entry(5, bob, 160, -1), entry(6, carol, 140, 0));
        // Quarter and full-season boards for placements
        stubBoard(QUARTER.getFrom(), 130, entry(3, testAccount, 500, 0), entry(5, alice, 480, 0), entry(12, bob, 400, 0));
        stubBoard(FULL_SEASON.getFrom(), 140, entry(4, testAccount, 2000, 0), entry(18, alice, 1800, 0), entry(25, bob, 1500, 0));
    }

    private static RoundSpan span(String code, int from, int to, PhaseType type) {
        return RoundSpan.builder().code(code).name(code).from(from).to(to).type(type).build();
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
        List<RoundSubmission> submissions = List.of(
                testAccount.submission,
                alice.submission,
                unverified.submission,
                optedOut.submission,
                bob.submission,
                carol.submission);
        List<RoundResult> results = List.of(
                testAccount.result, alice.result, unverified.result, optedOut.result, bob.result, carol.result);
        enqueuer.enqueue(season, round, ROUND, submissions, results);
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

        assertThat(alicePayload.quarter().label()).isEqualTo("Q3");
        assertThat(alicePayload.quarter().rank()).isEqualTo(5);
        assertThat(alicePayload.quarter().totalParticipants()).isEqualTo(130);

        assertThat(alicePayload.season().label()).isEqualTo("FS");
        assertThat(alicePayload.season().rank()).isEqualTo(18);
        assertThat(alicePayload.season().totalParticipants()).isEqualTo(140);

        RoundResultsPayload bobPayload = payloadOf(savedEvents().get(1));
        // Bob's round score (150) is below his sprint best (160)
        assertThat(bobPayload.sprint().isNewSprintBest()).isFalse();
        assertThat(bobPayload.sprint().movement()).isEqualTo(-1);
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
    void skipsEntirelyWhenSeasonHasNoMainContest() {
        season.setMainContestId(null);

        enqueue();

        verifyNoInteractions(outboxRepo);
        verifyNoInteractions(leaderboardRepo);
    }

    @Test
    void skipsEntirelyWhenNoPhasesConfigured() {
        when(competitionRepo.findById(competitionId))
                .thenReturn(Optional.of(
                        Competition.builder().id(competitionId).phases(List.of()).build()));

        enqueue();

        verifyNoInteractions(outboxRepo);
        verifyNoInteractions(leaderboardRepo);
    }

    @Test
    void usersWithoutARoundResultAreSkipped() {
        // Carol has a leaderboard entry but no result for this round
        List<RoundSubmission> submissions =
                List.of(testAccount.submission, alice.submission, unverified.submission, optedOut.submission);
        List<RoundResult> results = List.of(testAccount.result, alice.result, unverified.result, optedOut.result);

        enqueuer.enqueue(season, round, ROUND, submissions, results);

        List<OutboxEvent> events = savedEvents();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getIdempotencyKey()).contains(alice.id.toString());
    }

    @Test
    void serializationFailureForOneUserDoesNotStopOthers() {
        // Alice's user id collides with nothing; force failure by making save throw once for Alice
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
