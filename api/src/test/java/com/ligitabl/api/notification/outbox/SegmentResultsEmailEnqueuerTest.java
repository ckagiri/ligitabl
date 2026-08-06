package com.ligitabl.api.notification.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.AppSettingRepo;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.LeaderboardRepo;
import com.ligitabl.model.repo.OutboxRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.UserRepo;

/**
 * The phase table this suite is written against is the real one
 * ({@code seed/src/main/resources/seeding/competition.yaml}, PL): sprints end at 4, 9, 14, 19, 24,
 * 29, 34, 38 and quarters at 9, 19, 29, 38 — so <b>every quarter end is also a sprint end</b>, and
 * the combined case is half of all boundaries rather than an edge case.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Segment-results enqueuer")
class SegmentResultsEmailEnqueuerTest {

    private static final RoundSpan FULL_SEASON = span("FS", "Season", 1, 38, PhaseType.FULL_SEASON);
    private static final RoundSpan Q1 = span("Q1", "Quarter 1", 1, 9, PhaseType.QUARTER);
    private static final RoundSpan Q2 = span("Q2", "Quarter 2", 10, 19, PhaseType.QUARTER);
    private static final RoundSpan S1 = span("S1", "Sprint 1", 1, 4, PhaseType.SPRINT);
    private static final RoundSpan S2 = span("S2", "Sprint 2", 5, 9, PhaseType.SPRINT);
    private static final RoundSpan S3 = span("S3", "Sprint 3", 10, 14, PhaseType.SPRINT);

    private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");

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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SegmentResultsEmailProperties properties;
    private SegmentResultsEmailEnqueuer enqueuer;

    private final UUID seasonId = UUID.randomUUID();
    private final UUID competitionId = UUID.randomUUID();
    private final UUID contestId = UUID.randomUUID();

    private static int pubSeq = 0;

    /**
     * Public ids use {@code PublicId}'s ambiguity-free alphabet (no 0/1/l/o) — a UUID hex substring
     * fails validation, which is the trap {@code TestIds.randomPublicId} exists for.
     */
    private static final class TestUser {
        final UUID id = UUID.randomUUID();
        final String publicId;
        final User user;

        TestUser(String email, String displayName, boolean verified, boolean optedOut) {
            this.publicId = "aaaaaaaa" + "bcdefghjkm".charAt(pubSeq / 10 % 10) + "bcdefghjkm".charAt(pubSeq % 10);
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
    }

    private TestUser alice;
    private TestUser bob;
    private TestUser carol;
    private TestUser dave;
    private TestUser optedOut;
    private TestUser unverified;
    private TestUser testAccount;

    @BeforeEach
    void setup() {
        properties = new SegmentResultsEmailProperties();
        properties.setTopN(3);
        properties.setMode("live");
        properties.setDelay(Duration.ofDays(1));
        properties.setSeasonDelay(Duration.ofHours(1));

        enqueuer = new SegmentResultsEmailEnqueuer(
                outboxRepo,
                appSettingRepo,
                userRepo,
                leaderboardRepo,
                contestRepo,
                competitionRepo,
                seasonRepo,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC),
                properties);

        alice = new TestUser("alice@x.com", "Alice", true, false);
        bob = new TestUser("bob@x.com", "Bob", true, false);
        carol = new TestUser("carol@x.com", "Carol", true, false);
        dave = new TestUser("dave@x.com", "Dave", true, false);
        optedOut = new TestUser("optout@x.com", "Opted Out", true, true);
        unverified = new TestUser("unverified@x.com", "Unverified", false, false);
        testAccount = new TestUser("test@x.com", "Test Account", true, false);

        Season season = Season.builder()
                .id(seasonId)
                .competitionId(competitionId)
                .mainContestId(contestId)
                .maxRounds(38)
                .slug(SeasonSlug.of("2025-26"))
                .build();
        Competition competition = Competition.builder()
                .id(competitionId)
                .phases(List.of(FULL_SEASON, Q1, Q2, S1, S2, S3))
                .build();
        Contest contest =
                Contest.builder().id(contestId).seasonId(seasonId).name("Main").build();

        when(seasonRepo.findById(seasonId)).thenReturn(Optional.of(season));
        when(contestRepo.findById(contestId)).thenReturn(Optional.of(contest));
        when(competitionRepo.findById(competitionId)).thenReturn(Optional.of(competition));
        when(appSettingRepo.findValue(SegmentResultsEmailEnqueuer.IGNORE_LIST_SETTING_KEY))
                .thenReturn(Optional.of(" Test@X.com , "));
        when(outboxRepo.save(any())).thenReturn(true);

        for (TestUser t : List.of(alice, bob, carol, dave, optedOut, unverified, testAccount)) {
            when(userRepo.findByPublicId(PublicId.create(t.publicId))).thenReturn(Optional.of(t.user));
        }
    }

    // ---------------------------------------------------------------- helpers

    private static RoundSpan span(String code, String name, int from, int to, PhaseType type) {
        return RoundSpan.builder()
                .code(code)
                .name(name)
                .from(from)
                .to(to)
                .type(type)
                .build();
    }

    private static LeaderboardEntry entry(TestUser user, int position, int totalScore) {
        return new LeaderboardEntry(
                position, user.publicId, user.user.getDisplayName(), totalScore, 0, 0, 0, 0, 1, 0, true, false);
    }

    /** Wires the board for one segment's exact window, so a wrong window returns nothing. */
    private void board(RoundSpan span, int totalParticipants, LeaderboardEntry... entries) {
        when(leaderboardRepo.computeLeaderboard(
                        eq(contestId),
                        eq(seasonId),
                        eq(span.getFrom()),
                        eq(span.getTo()),
                        isNull(),
                        anyInt(),
                        anyInt(),
                        anyBoolean()))
                .thenReturn(new LeaderboardResponse(
                        List.of(entries), null, false, 0, totalParticipants, false, false, span.getTo()));
    }

    private List<OutboxEvent> savedEvents() {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepo, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    private SegmentResultsPayload payloadOf(OutboxEvent event) {
        try {
            return objectMapper.readValue(event.getPayload(), SegmentResultsPayload.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private SegmentResultsPayload payloadFor(TestUser user) {
        return savedEvents().stream()
                .map(this::payloadOf)
                .filter(p -> p.userId().equals(user.id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no event for " + user.user.getDisplayName()));
    }

    // ------------------------------------------------------------- boundaries

    @Nested
    @DisplayName("Boundary detection")
    class Boundaries {

        @Test
        @DisplayName("a mid-sprint round closes nothing and writes no events")
        void midSprintRoundIsANoOp() {
            enqueuer.enqueueForRound(new RoundAdvancedPayload(seasonId, 7, 8));

            verify(outboxRepo, never()).save(any());
            verify(leaderboardRepo, never())
                    .computeLeaderboard(any(), any(), anyInt(), anyInt(), any(), anyInt(), anyInt(), anyBoolean());
        }

        /**
         * Rounds 5–8 sit inside S2 with no segment ending. Asserted as a group because a
         * single-round case would pass against an implementation that only special-cased that one.
         */
        @Test
        @DisplayName("no round between two boundaries produces anything")
        void everyMidSegmentRoundIsANoOp() {
            for (int round : new int[] {5, 6, 7, 8, 11, 12, 13}) {
                enqueuer.enqueueForRound(new RoundAdvancedPayload(seasonId, round, round + 1));
            }

            verify(outboxRepo, never()).save(any());
        }

        @Test
        @DisplayName("a sprint-only boundary reports the sprint alone")
        void sprintOnlyBoundary() {
            board(S1, 41, entry(alice, 1, 90), entry(bob, 2, 85), entry(carol, 3, 80));

            enqueuer.enqueueForRound(new RoundAdvancedPayload(seasonId, 4, 5));

            assertThat(savedEvents()).hasSize(3);
            SegmentResultsPayload payload = payloadFor(alice);
            assertThat(payload.placements()).singleElement().satisfies(p -> {
                assertThat(p.code()).isEqualTo("S1");
                assertThat(p.type()).isEqualTo("SPRINT");
                assertThat(p.rank()).isEqualTo(1);
                assertThat(p.totalParticipants()).isEqualTo(41);
                assertThat(p.totalScore()).isEqualTo(90);
                assertThat(p.fromRound()).isEqualTo(1);
                assertThat(p.toRound()).isEqualTo(4);
            });
        }

        /**
         * The quarter must be read over its own window (1–9), not the boundary sprint's (5–9).
         * Wiring boards by exact window means a wrong window would yield an empty board and no
         * quarter block at all.
         */
        @Test
        @DisplayName("a sprint+quarter boundary reports both, over their own windows")
        void combinedBoundaryReportsBothSegments() {
            board(S2, 41, entry(alice, 1, 95), entry(bob, 2, 90), entry(carol, 3, 88));
            board(Q1, 58, entry(alice, 2, 390), entry(dave, 1, 400), entry(carol, 3, 380));

            enqueuer.enqueueForRound(new RoundAdvancedPayload(seasonId, 9, 10));

            SegmentResultsPayload alicePayload = payloadFor(alice);
            assertThat(alicePayload.placements())
                    .extracting(SegmentResultsPayload.SegmentPlacement::code)
                    .as("sprint first, then quarter — smallest window first")
                    .containsExactly("S2", "Q1");
            assertThat(alicePayload.placements().get(1).fromRound()).isEqualTo(1);
            assertThat(alicePayload.placements().get(1).toRound()).isEqualTo(9);
            assertThat(alicePayload.placements().get(1).totalParticipants()).isEqualTo(58);
        }

        @Test
        @DisplayName("a user on only one of the two podiums gets only that block")
        void singlePodiumUserGetsOneBlock() {
            board(S2, 41, entry(alice, 1, 95), entry(bob, 2, 90), entry(carol, 3, 88));
            board(Q1, 58, entry(alice, 2, 390), entry(dave, 1, 400), entry(carol, 3, 380));

            enqueuer.enqueueForRound(new RoundAdvancedPayload(seasonId, 9, 10));

            assertThat(payloadFor(bob).placements())
                    .extracting(SegmentResultsPayload.SegmentPlacement::code)
                    .containsExactly("S2");
            assertThat(payloadFor(dave).placements())
                    .extracting(SegmentResultsPayload.SegmentPlacement::code)
                    .containsExactly("Q1");
        }

        /**
         * The whole point of combining. Two events for one person at one boundary would also
         * collide on the idempotency key, so the second would be silently dropped.
         */
        @Test
        @DisplayName("a user on both podiums gets exactly one email, not two")
        void doubleFinisherGetsOneEmail() {
            board(S2, 41, entry(alice, 1, 95), entry(bob, 2, 90), entry(carol, 3, 88));
            board(Q1, 58, entry(alice, 2, 390), entry(dave, 1, 400), entry(carol, 3, 380));

            enqueuer.enqueueForRound(new RoundAdvancedPayload(seasonId, 9, 10));

            assertThat(savedEvents().stream()
                            .map(SegmentResultsEmailEnqueuerTest.this::payloadOf)
                            .filter(p -> p.userId().equals(alice.id)))
                    .hasSize(1);
            assertThat(savedEvents())
                    .as("alice, bob, carol, dave — four people, four emails")
                    .hasSize(4);
        }
    }

    // ------------------------------------------------------------- recipients

    @Nested
    @DisplayName("Recipient selection")
    class Recipients {

        /**
         * The property that separates this enqueuer from RoundResultsEmailEnqueuer: rank is the
         * content, so a filtered-out finisher's slot is not handed to the next rank down.
         */
        @Test
        @DisplayName("an opted-out finisher's slot is NOT backfilled from below")
        void noBackfill() {
            board(S1, 41, entry(alice, 1, 90), entry(optedOut, 2, 85), entry(carol, 3, 80), entry(dave, 4, 75));

            enqueuer.enqueueForRound(new RoundAdvancedPayload(seasonId, 4, 5));

            assertThat(savedEvents()).as("two recipients, not three").hasSize(2);
            assertThat(savedEvents().stream()
                            .map(SegmentResultsEmailEnqueuerTest.this::payloadOf)
                            .map(SegmentResultsPayload::userId))
                    .containsExactlyInAnyOrder(alice.id, carol.id)
                    .doesNotContain(dave.id);
        }

        @Test
        @DisplayName("unverified and ignore-list accounts are skipped in live mode")
        void liveModeFilters() {
            board(S1, 41, entry(unverified, 1, 90), entry(testAccount, 2, 85), entry(alice, 3, 80));

            enqueuer.enqueueForRound(new RoundAdvancedPayload(seasonId, 4, 5));

            assertThat(savedEvents()).hasSize(1);
            assertThat(payloadFor(alice).placements().get(0).rank())
                    .as("alice keeps her real rank of 3; she is not promoted to 1st")
                    .isEqualTo(3);
        }

        @Test
        @DisplayName("test mode inverts: only ignore-list accounts receive")
        void testModeInverts() {
            properties.setMode("test");
            board(S1, 41, entry(alice, 1, 90), entry(testAccount, 2, 85), entry(carol, 3, 80));

            enqueuer.enqueueForRound(new RoundAdvancedPayload(seasonId, 4, 5));

            assertThat(savedEvents()).hasSize(1);
            assertThat(payloadOf(savedEvents().get(0)).userId()).isEqualTo(testAccount.id);
        }

        @Test
        @DisplayName("an empty podium writes nothing rather than an empty email")
        void nobodyMailableWritesNothing() {
            board(S1, 41, entry(unverified, 1, 90), entry(optedOut, 2, 85));

            enqueuer.enqueueForRound(new RoundAdvancedPayload(seasonId, 4, 5));

            verify(outboxRepo, never()).save(any());
        }

        @Test
        @DisplayName("only topN are considered, however long the board is")
        void respectsTopN() {
            properties.setTopN(2);
            board(S1, 41, entry(alice, 1, 90), entry(bob, 2, 85), entry(carol, 3, 80));

            enqueuer.enqueueForRound(new RoundAdvancedPayload(seasonId, 4, 5));

            assertThat(savedEvents()).hasSize(2);
        }
    }

    // ------------------------------------------------------------------ event

    @Nested
    @DisplayName("Event shape")
    class EventShape {

        @Test
        @DisplayName("events are held back by the configured delay")
        void appliesTheDelay() {
            board(S1, 41, entry(alice, 1, 90));

            enqueuer.enqueueForRound(new RoundAdvancedPayload(seasonId, 4, 5));

            assertThat(savedEvents().get(0).getAvailableAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
        }

        @Test
        @DisplayName("the season finale uses the shorter seasonDelay, not the round delay")
        void seasonUsesItsOwnDelay() {
            board(FULL_SEASON, 61, entry(alice, 1, 1500));

            enqueuer.enqueueForSeasonCompleted(new SeasonCompletedPayload(seasonId));

            assertThat(savedEvents().get(0).getAvailableAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        }

        @Test
        @DisplayName("the two delays are applied independently")
        void roundAndSeasonDelaysDoNotShareAValue() {
            board(S1, 41, entry(alice, 1, 90));
            board(FULL_SEASON, 61, entry(alice, 1, 1500));

            enqueuer.enqueueForRound(new RoundAdvancedPayload(seasonId, 4, 5));
            enqueuer.enqueueForSeasonCompleted(new SeasonCompletedPayload(seasonId));

            assertThat(savedEvents())
                    .extracting(OutboxEvent::getAvailableAt)
                    .containsExactly(NOW.plus(Duration.ofDays(1)), NOW.plus(Duration.ofHours(1)));
        }

        @Test
        @DisplayName("a zero delay makes the event immediately claimable")
        void zeroDelayIsImmediate() {
            properties.setDelay(Duration.ZERO);
            board(S1, 41, entry(alice, 1, 90));

            enqueuer.enqueueForRound(new RoundAdvancedPayload(seasonId, 4, 5));

            assertThat(savedEvents().get(0).getAvailableAt()).isEqualTo(NOW);
        }

        /**
         * The collision this key exists to prevent: at round 38 the sprint/quarter boundary and the
         * later season completion share season, round and user, so a key without the scope would
         * make the second insert a no-op and the champion would never be told.
         */
        @Test
        @DisplayName("round and season scopes produce different idempotency keys")
        void scopeKeysDoNotCollide() {
            board(S1, 41, entry(alice, 1, 90));
            board(FULL_SEASON, 61, entry(alice, 1, 1500));

            enqueuer.enqueueForRound(new RoundAdvancedPayload(seasonId, 4, 5));
            enqueuer.enqueueForSeasonCompleted(new SeasonCompletedPayload(seasonId));

            assertThat(savedEvents())
                    .extracting(OutboxEvent::getIdempotencyKey)
                    .containsExactly(
                            "segment-results:%s:r4:%s".formatted(seasonId, alice.id),
                            "segment-results:%s:season:%s".formatted(seasonId, alice.id));
        }

        @Test
        @DisplayName("season completion reports the full-season window")
        void seasonCompletionReportsTheSeason() {
            board(FULL_SEASON, 61, entry(alice, 1, 1500), entry(bob, 2, 1450));

            enqueuer.enqueueForSeasonCompleted(new SeasonCompletedPayload(seasonId));

            assertThat(savedEvents()).hasSize(2);
            assertThat(payloadFor(alice).placements()).singleElement().satisfies(p -> {
                assertThat(p.type()).isEqualTo("FULL_SEASON");
                assertThat(p.fromRound()).isEqualTo(1);
                assertThat(p.toRound()).isEqualTo(38);
                assertThat(p.rank()).isEqualTo(1);
            });
            assertThat(payloadFor(alice).boundaryRound()).isEqualTo(38);
            assertThat(payloadFor(alice).scopeKey()).isEqualTo("season");
        }

        @Test
        @DisplayName("the payload carries what the template needs without further queries")
        void payloadIsSelfContained() {
            board(S1, 41, entry(alice, 1, 90));

            enqueuer.enqueueForRound(new RoundAdvancedPayload(seasonId, 4, 5));

            SegmentResultsPayload payload = payloadFor(alice);
            assertThat(payload.userEmail()).isEqualTo("alice@x.com");
            assertThat(payload.userDisplayName()).isEqualTo("Alice");
            assertThat(payload.userPublicId()).isEqualTo(alice.publicId);
            assertThat(payload.seasonSlug()).isNotBlank();
            assertThat(payload.boundaryRound()).isEqualTo(4);
        }
    }

    // ------------------------------------------------------------ degradation

    @Nested
    @DisplayName("Missing configuration")
    class Degradation {

        @Test
        @DisplayName("an unknown season is skipped, not thrown")
        void unknownSeasonIsSkipped() {
            UUID unknown = UUID.randomUUID();
            when(seasonRepo.findById(unknown)).thenReturn(Optional.empty());

            enqueuer.enqueueForRound(new RoundAdvancedPayload(unknown, 4, 5));

            verify(outboxRepo, never()).save(any());
        }

        @Test
        @DisplayName("a competition with no phases is skipped")
        void noPhasesIsSkipped() {
            when(competitionRepo.findById(competitionId))
                    .thenReturn(Optional.of(Competition.builder()
                            .id(competitionId)
                            .phases(List.of())
                            .build()));

            enqueuer.enqueueForRound(new RoundAdvancedPayload(seasonId, 4, 5));

            verify(outboxRepo, never()).save(any());
        }

        @Test
        @DisplayName("season completion with no FULL_SEASON phase is skipped")
        void noFullSeasonPhaseIsSkipped() {
            when(competitionRepo.findById(competitionId))
                    .thenReturn(Optional.of(Competition.builder()
                            .id(competitionId)
                            .phases(List.of(Q1, S1))
                            .build()));

            enqueuer.enqueueForSeasonCompleted(new SeasonCompletedPayload(seasonId));

            verify(outboxRepo, never()).save(any());
        }
    }
}
