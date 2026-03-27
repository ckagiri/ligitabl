package com.ligitabl.api.scheduling.advanceround;

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

import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Round Advancement Service
 *
 * Handles automatic round advancement with a configurable delay window.
 *
 * Flow:
 * 1. Round finalized → scheduleAdvancement() called by MatchSyncScheduler
 * 2. advanceAt persisted to DB (survives restarts)
 * 3. One-time scheduled task fires after delay
 * 4. attemptAutoAdvancement() advances the round (or skips if cancelled/overridden)
 *
 * Admin overrides:
 * - advance-now: calls advanceManually(), bypasses the delay
 * - cancel-advancement: clears advanceAt, scheduled task is a no-op when it fires
 *
 * TODO: email notifications (pre-advancement warning, post-advancement confirmation, failure alert)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoundAdvancementService {

    private final TaskScheduler taskScheduler;
    private final RoundRepo roundRepo;
    private final SeasonRepo seasonRepo;
    private final Clock clock;

    @Value("${ligitabl.round-advancement.delay-minutes:3}")
    private int delayMinutes;

    /**
     * Schedule automatic round advancement after the configured delay.
     * Persists advanceAt to DB so RoundAdvancementRecovery can re-trigger on restart.
     *
     * @param finalizedRoundId round that was just finalized (must equal season.currentRoundId)
     * @param seasonId         season containing the round
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

        round.setAdvanceAt(advanceAt);
        round.setAdvanced(false);
        roundRepo.save(round);

        taskScheduler.schedule(() -> attemptAutoAdvancement(finalizedRoundId, seasonId), advanceTime);

        log.info("Scheduled advancement for round={} at {} ({} minutes)", finalizedRoundId, advanceAt, delayMinutes);
        // TODO: send pre-advancement email to admin
    }

    /**
     * Attempt automatic advancement. Idempotent — safe to call from both the
     * scheduled task and RoundAdvancementRecovery.
     *
     * @param roundId  round to advance
     * @param seasonId season containing the round
     */
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
            // TODO: send post-advancement confirmation email to admin

        } catch (Exception e) {
            log.error("Failed to auto-advance round={}", roundId, e);
            // TODO: send advancement failure email to admin
        }
    }

    /**
     * Manually advance the round immediately, bypassing the delay window.
     * Called by the admin advance-now endpoint.
     *
     * @param roundId round to advance
     * @return true if advanced, false if already advanced
     */
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
        return true;
    }

    /**
     * Cancel the scheduled auto-advancement by clearing advanceAt.
     * The scheduled task will be a no-op when it fires.
     * The round remains FINALIZED; admin must use advanceManually() when ready.
     *
     * @param roundId round whose advancement to cancel
     */
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

        round.setAdvanceAt(null);
        roundRepo.save(round);

        log.info("Cancelled scheduled advancement for round={}", roundId);
    }

    /**
     * Core advancement logic. Marks current round as advanced and opens next round,
     * or completes the season if this was the last round.
     */
    private void advanceToNextRound(Season season, Round currentRound) {
        int currentPosition = currentRound.getPosition();

        if (currentPosition >= season.getMaxRounds()) {
            if (!season.isCompleted()) {
                season.setCompleted(true);
                season.setCompletedAt(now());
                seasonRepo.save(season);
                log.info("Season completed: seasonId={}", season.getId());
            }
            return;
        }

        currentRound.setAdvanced(true);
        currentRound.setAdvancedAt(now());
        roundRepo.save(currentRound);

        var nextRound = roundRepo
                .findBySeasonIdAndPosition(season.getId(), currentPosition + 1)
                .orElseThrow(() -> new IllegalStateException(
                        "Next round not found: seasonId=" + season.getId() + " position=" + (currentPosition + 1)));

        season.setCurrentRoundId(nextRound.getId());
        seasonRepo.save(season);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
