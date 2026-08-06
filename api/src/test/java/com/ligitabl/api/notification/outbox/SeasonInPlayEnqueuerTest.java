package com.ligitabl.api.notification.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.round.shared.RoundSupport;
import com.ligitabl.api.testsupport.TestClock;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.OutboxRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

/**
 * Almost every case here asserts that nothing is written. That is the point: this enqueuer runs
 * every 15 minutes and spends nearly all of its life legitimately skipping, so the guards are
 * the behaviour worth pinning — a regression that makes it fire early is far more damaging than
 * one that makes it fire late, since ROUND_LOCKED is the designed catch-up path.
 *
 * <p>{@link Season#isInPlay()} reads the wall clock rather than an injected {@code Clock}, so
 * fixtures are built relative to {@code OffsetDateTime.now()} instead of a frozen instant.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SeasonInPlayEnqueuerTest {

    private static final UUID SEASON_ID = UUID.randomUUID();
    private static final UUID ROUND_ID = UUID.randomUUID();

    @Mock
    SeasonRepo seasonRepo;

    @Mock
    RoundRepo roundRepo;

    @Mock
    RoundSupport roundSupport;

    @Mock
    OutboxRepo outboxRepo;

    private final CompetitionDefaults competitionDefaults = new CompetitionDefaults("pl");
    private final ObjectMapper objectMapper = new ObjectMapper();

    private SeasonInPlayEnqueuer enqueuer;

    @BeforeEach
    void setup() {
        enqueuer = new SeasonInPlayEnqueuer(
                seasonRepo, roundRepo, roundSupport, competitionDefaults, outboxRepo, objectMapper, TestClock.FIXED);

        when(seasonRepo.findActiveSeason("pl"))
                .thenReturn(Optional.of(inPlaySeason().build()));
        when(roundRepo.findById(ROUND_ID)).thenReturn(Optional.of(round(1, false)));
        when(roundSupport.resolveJoinEligibilityStatus(any())).thenReturn(RoundStatus.OPEN);
        when(outboxRepo.save(any())).thenReturn(true);
    }

    /** In play: pre-season opened, predictions opened, not completed, round 1 current. */
    private Season.SeasonBuilder<?, ?> inPlaySeason() {
        OffsetDateTime now = OffsetDateTime.now();
        return Season.builder()
                .id(SEASON_ID)
                .mainContestId(UUID.randomUUID())
                .currentRoundId(ROUND_ID)
                .startDate(TestClock.TODAY.minusDays(1))
                .endDate(TestClock.TODAY.plusMonths(9))
                .completed(false)
                .preSeasonOpensAt(now.minusDays(30))
                .predictionsOpenAt(now.minusHours(1))
                .initialRankings(List.of(new TeamRank("MCI", 1), new TeamRank("ARS", 2)));
    }

    private Round round(int position, boolean finalized) {
        return Round.builder()
                .id(ROUND_ID)
                .seasonId(SEASON_ID)
                .name("Round " + position)
                .slug("round-" + position)
                .position(position)
                .finalized(finalized)
                .build();
    }

    private void activeSeason(Season season) {
        when(seasonRepo.findActiveSeason("pl")).thenReturn(Optional.of(season));
    }

    @Test
    void enqueuesOnceWithTheSeasonScopedKey() {
        enqueuer.enqueueIfSeasonInPlay();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepo).save(captor.capture());
        OutboxEvent event = captor.getValue();
        Assertions.assertThat(event.getIdempotencyKey()).isEqualTo("season-in-play:" + SEASON_ID);
        Assertions.assertThat(event.getEventType()).isEqualTo(OutboxEventTypes.SEASON_IN_PLAY);
        Assertions.assertThat(event.getAggregateType()).isEqualTo("season");
        Assertions.assertThat(event.getPayload()).contains(SEASON_ID.toString());
    }

    @Test
    void enqueuesWhenRoundOneHasNoMatchesSyncedYet() {
        // A round with no matches loaded resolves OPEN, which is exactly the state at the moment
        // predictions open — fixtures may not be in place yet (eg when testing).
        when(roundSupport.resolveJoinEligibilityStatus(any())).thenReturn(RoundStatus.OPEN);

        enqueuer.enqueueIfSeasonInPlay();

        verify(outboxRepo).save(any());
    }

    @Test
    void skips_whenNoActiveSeason() {
        when(seasonRepo.findActiveSeason("pl")).thenReturn(Optional.empty());

        enqueuer.enqueueIfSeasonInPlay();

        verify(outboxRepo, never()).save(any());
    }

    @Test
    void skips_whenSeasonStillInPreSeason() {
        activeSeason(inPlaySeason()
                .predictionsOpenAt(OffsetDateTime.now().plusDays(3))
                .startDate(TestClock.TODAY.plusDays(3))
                .build());

        enqueuer.enqueueIfSeasonInPlay();

        verify(outboxRepo, never()).save(any());
    }

    @Test
    void skips_whenSeasonCompleted() {
        activeSeason(inPlaySeason().completed(true).build());

        enqueuer.enqueueIfSeasonInPlay();

        verify(outboxRepo, never()).save(any());
    }

    @Test
    void skips_whenSeasonInSetupMode() {
        // No main contest to join.
        activeSeason(inPlaySeason().mainContestId(null).build());

        enqueuer.enqueueIfSeasonInPlay();

        verify(outboxRepo, never()).save(any());
    }

    @Test
    void skips_whenSeasonHasNoPreSeasonOpensAt() {
        // The eligibility anchor; without it there is no defensible cohort.
        activeSeason(inPlaySeason().preSeasonOpensAt(null).build());

        enqueuer.enqueueIfSeasonInPlay();

        verify(outboxRepo, never()).save(any());
    }

    @Test
    void skips_whenSeasonHasNoInitialRankings() {
        // registerPreSeason applies swaps to this baseline; null would NPE per user.
        activeSeason(inPlaySeason().initialRankings(List.of()).build());

        enqueuer.enqueueIfSeasonInPlay();

        verify(outboxRepo, never()).save(any());
    }

    @Test
    void skips_whenCurrentRoundCannotBeResolved() {
        when(roundRepo.findById(ROUND_ID)).thenReturn(Optional.empty());

        enqueuer.enqueueIfSeasonInPlay();

        verify(outboxRepo, never()).save(any());
    }

    @Test
    void skips_whenRoundOneIsAlreadyLocked() {
        // The late-fire guard: round-0 rows are scored from round 1, so creating them now would
        // leave these users a wrong result for it.
        when(roundSupport.resolveJoinEligibilityStatus(any())).thenReturn(RoundStatus.LOCKED);

        enqueuer.enqueueIfSeasonInPlay();

        verify(outboxRepo, never()).save(any());
    }

    @Test
    void skips_whenRoundOneIsFinalized() {
        // The finalized-vs-no-matches subtlety lives in RoundSupport (see RoundSupportTest);
        // here we only pin that the enqueuer refuses anything other than OPEN.
        when(roundRepo.findById(ROUND_ID)).thenReturn(Optional.of(round(1, true)));
        when(roundSupport.resolveJoinEligibilityStatus(any())).thenReturn(RoundStatus.FINALIZED);

        enqueuer.enqueueIfSeasonInPlay();

        verify(outboxRepo, never()).save(any());
    }

    @Test
    void skips_whenSeasonHasMovedPastRoundOne() {
        // Open, but not round 1 — position alone must not be enough.
        when(roundRepo.findById(ROUND_ID)).thenReturn(Optional.of(round(5, false)));
        when(roundSupport.resolveJoinEligibilityStatus(any())).thenReturn(RoundStatus.OPEN);

        enqueuer.enqueueIfSeasonInPlay();

        verify(outboxRepo, never()).save(any());
    }

    @Test
    void alreadyEnqueued_isASilentNoOp() {
        when(outboxRepo.save(any())).thenReturn(false);

        enqueuer.enqueueIfSeasonInPlay();

        verify(outboxRepo).save(any());
    }

    @Test
    void enqueueFailure_isSwallowedSoThePollerSurvives() {
        when(outboxRepo.save(any())).thenThrow(new RuntimeException("db down"));

        enqueuer.enqueueIfSeasonInPlay();

        verify(outboxRepo).save(any());
    }
}
