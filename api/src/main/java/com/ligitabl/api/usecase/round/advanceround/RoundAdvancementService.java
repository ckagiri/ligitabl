package com.ligitabl.api.usecase.round.advanceround;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.usecase.shared.AdminAlerts;
import com.ligitabl.api.usecase.shared.outbox.OutboxEventTypes;
import com.ligitabl.api.usecase.shared.outbox.RoundAdvancedPayload;
import com.ligitabl.model.domain.league.Round;
import com.ligitabl.model.domain.league.Season;
import com.ligitabl.model.outbox.OutboxEvent;
import com.ligitabl.model.repo.OutboxRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Automatic round advancement after a configurable delay. A finalized round gets
 * {@code scheduleAdvancement()}; {@code advanceAt} is persisted so the advance survives restarts,
 * and a one-time task runs {@code attemptAutoAdvancement()}. Admins can advance now
 * ({@code advanceManually()}) or cancel, which clears {@code advanceAt} so the task no-ops.
 *
 * Admin notifications: pre-advancement warning (the cancel window), post-advancement
 * confirmation, and a failure alert — via {@link AdminAlerts}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoundAdvancementService {

    private final TaskScheduler taskScheduler;
    private final RoundRepo roundRepo;
    private final SeasonRepo seasonRepo;
    private final Clock clock;
    private final AdminAlerts adminAlerts;
    private final OutboxRepo outboxRepo;
    private final ObjectMapper objectMapper;

    @Value("${ligitabl.round-advancement.delay-minutes:3}")
    private int delayMinutes;

    /**
     * Persists advanceAt so RoundAdvancementRecovery can re-trigger on restart.
     *
     * @param finalizedRoundId must equal season.currentRoundId
     */
    public void scheduleAdvancement(UUID finalizedRoundId, UUID seasonId) {
        var season = seasonRepo
                .findById(seasonId)
                .orElseThrow(() -> new IllegalArgumentException("Season not found: " + seasonId));

        if (!finalizedRoundId.equals(season.getCurrentRoundId())) {
            throw new IllegalArgumentException(
                    "Cannot schedule advancement: finalizedRoundId does not match season.currentRoundId");
        }

        var round = roundRepo
                .findById(finalizedRoundId)
                .orElseThrow(() -> new IllegalArgumentException("Round not found: " + finalizedRoundId));

        if (!round.isFinalized()) {
            throw new IllegalArgumentException(
                    "Cannot schedule advancement: round is not finalized: " + finalizedRoundId);
        }

        Instant advanceTime = clock.instant().plus(Duration.ofMinutes(delayMinutes));
        if (advanceTime == null) {
            throw new IllegalStateException("Failed to compute advancement time");
        }
        OffsetDateTime advanceAt = OffsetDateTime.ofInstant(advanceTime, ZoneOffset.UTC);

        round.scheduleAdvance(advanceAt);
        roundRepo.save(round);

        taskScheduler.schedule(() -> attemptAutoAdvancement(finalizedRoundId, seasonId), advanceTime);

        log.info("Scheduled advancement for round={} at {} ({} minutes)", finalizedRoundId, advanceAt, delayMinutes);
        adminAlerts.notifyAdvancementScheduled(finalizedRoundId, round.getPosition(), advanceAt, delayMinutes);
    }

    /** Idempotent, so both the scheduled task and RoundAdvancementRecovery may call it. */
    @Transactional
    public void attemptAutoAdvancement(UUID roundId, UUID seasonId) {
        try {
            var round = roundRepo
                    .findById(roundId)
                    .orElseThrow(() -> new IllegalStateException("Round not found: " + roundId));

            if (round.getAdvanceAt() == null) {
                log.info("Skipping auto-advancement for round={}: advancement was cancelled", roundId);
                return;
            }

            if (round.isAdvanced()) {
                log.info("Skipping auto-advancement for round={}: already advanced (manual override)", roundId);
                return;
            }

            if (!round.isFinalized()) {
                log.warn("Skipping auto-advancement for round={}: status is not FINALIZED", roundId);
                return;
            }

            var season = seasonRepo
                    .findById(seasonId)
                    .orElseThrow(() -> new IllegalStateException("Season not found: " + seasonId));

            advanceToNextRound(season, round);

            log.info("Auto-advanced from round position={} (seasonId={})", round.getPosition(), seasonId);
            adminAlerts.notifyRoundAdvanced(
                    round.getPosition(), seasonId, round.getPosition() >= season.getMaxRounds());

        } catch (Exception e) {
            log.error("Failed to auto-advance round={}", roundId, e);
            Sentry.captureException(e);
            adminAlerts.notifyAdvancementFailed(roundId, e.getMessage());
        }
    }

    /** For the admin advance-now endpoint. Returns false if already advanced. */
    public boolean advanceManually(UUID roundId) {
        var round = roundRepo
                .findById(roundId)
                .orElseThrow(() -> new IllegalArgumentException("Round not found: " + roundId));

        if (round.isAdvanced()) {
            log.info("advanceManually: round={} already advanced", roundId);
            return false;
        }

        if (!round.isFinalized()) {
            throw new IllegalStateException("Cannot advance non-finalized round: " + roundId);
        }

        var season = seasonRepo
                .findById(round.getSeasonId())
                .orElseThrow(() -> new IllegalStateException("Season not found: " + round.getSeasonId()));

        advanceToNextRound(season, round);

        log.info("Manually advanced round={} (position={})", roundId, round.getPosition());
        adminAlerts.notifyRoundAdvanced(
                round.getPosition(), round.getSeasonId(), round.getPosition() >= season.getMaxRounds());
        return true;
    }

    /** Clears advanceAt so the scheduled task no-ops. The round stays FINALIZED until advanced manually. */
    public void cancelScheduledAdvancement(UUID roundId) {
        var round = roundRepo
                .findById(roundId)
                .orElseThrow(() -> new IllegalArgumentException("Round not found: " + roundId));

        if (round.isAdvanced()) {
            throw new IllegalStateException("Round already advanced: " + roundId);
        }

        if (round.getAdvanceAt() == null) {
            throw new IllegalStateException("No advancement scheduled for round: " + roundId);
        }

        round.cancelScheduledAdvance();
        roundRepo.save(round);

        log.info("Cancelled scheduled advancement for round={}", roundId);
    }

    /**
     * Core advancement logic. Marks current round as advanced and opens next round,
     * or completes the season if this was the last round.
     */
    private void advanceToNextRound(Season season, Round currentRound) {
        int currentPosition = currentRound.getPosition();

        currentRound.markAdvanced(now());
        roundRepo.save(currentRound);

        boolean isLastRound = currentPosition >= season.getMaxRounds();
        int newCurrentRoundPosition = currentPosition;

        if (isLastRound) {
            log.info(
                    "Last round advanced: seasonId={}, roundPosition={}. Season completion is a separate admin action.",
                    season.getId(),
                    currentPosition);
        } else {
            var nextRound = roundRepo
                    .findBySeasonIdAndPosition(season.getId(), currentPosition + 1)
                    .orElseThrow(() -> new IllegalStateException(
                            "Next round not found: seasonId=" + season.getId() + " position=" + (currentPosition + 1)));

            season.moveToRound(nextRound);
            seasonRepo.save(season);
            newCurrentRoundPosition = nextRound.getPosition();
        }

        enqueueRoundResultsEmail(season, currentPosition, newCurrentRoundPosition);
    }

    /**
     * Records the round-advanced fact in the outbox, in the advancement's transaction. The relay
     * expands it into per-user ROUND_RESULTS events; a failure here logs but never blocks advancing.
     */
    private void enqueueRoundResultsEmail(Season season, int roundPosition, int currentRoundPosition) {
        try {
            RoundAdvancedPayload payload =
                    new RoundAdvancedPayload(season.getId(), roundPosition, currentRoundPosition);
            outboxRepo.save(OutboxEvent.create(
                    "round-advanced:%s:%d".formatted(season.getId(), roundPosition),
                    OutboxEventTypes.ROUND_ADVANCED,
                    "round",
                    String.valueOf(roundPosition),
                    objectMapper.writeValueAsString(payload)));
        } catch (Exception e) {
            // The round advanced but nobody gets the results email — worth an alert, not just a log.
            log.error("[ROUND_ADVANCED_OUTBOX_FAILED] round={}: {}", roundPosition, e.getMessage(), e);
            Sentry.captureException(e);
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
