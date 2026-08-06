package com.ligitabl.api.notification.outbox;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.round.shared.RoundSupport;
import com.ligitabl.model.domain.OutboxEvent;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.OutboxRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Writes the one-shot SEASON_IN_PLAY event when the season opens for play.
 *
 * <p>{@code SeasonState} is derived rather than persisted, so there is no transition hook to
 * subscribe to — this polls, and the unique idempotency key {@code season-in-play:{seasonId}}
 * (ON CONFLICT DO NOTHING) is what makes it fire exactly once per season. Every subsequent poll
 * is a cheap no-op insert.
 *
 * <p>Skip reasons log at debug deliberately: this runs every 15 minutes and spends almost all of
 * its life legitimately skipping. Only an actual insert is worth an info line.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeasonInPlayEnqueuer {

    private static final int FIRST_ROUND = 1;

    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final RoundSupport roundSupport;
    private final CompetitionDefaults competitionDefaults;
    private final OutboxRepo outboxRepo;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public void enqueueIfSeasonInPlay() {
        Season season = seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .orElse(null);
        if (season == null) {
            log.debug("[SEASON_IN_PLAY_SKIPPED] no active season");
            return;
        }
        if (season.isCompleted() || !season.isInPlay(clock.instant())) {
            log.debug("[SEASON_IN_PLAY_SKIPPED] season {} is not in play", season.getId());
            return;
        }
        if (season.isInSetupMode()) {
            log.debug("[SEASON_IN_PLAY_SKIPPED] season {} is in setup mode", season.getId());
            return;
        }
        if (season.getPreSeasonOpensAt() == null) {
            // The eligibility anchor. Without it there is no defensible cohort to auto-join.
            log.debug("[SEASON_IN_PLAY_SKIPPED] season {} has no preSeasonOpensAt", season.getId());
            return;
        }
        if (season.getInitialRankings() == null || season.getInitialRankings().isEmpty()) {
            // registerPreSeason applies swaps to this baseline; a null one would NPE per user.
            log.debug("[SEASON_IN_PLAY_SKIPPED] season {} has no initial rankings", season.getId());
            return;
        }
        if (!isFirstRoundStillOpen(season)) {
            return;
        }

        writeEvent(season.getId());
    }

    /**
     * The late-fire guard. Round-0 rows are scored from round 1 onwards, so creating them after
     * round 1 has locked would leave those users a missing or wrong result for it. Missing the
     * window must therefore mean "no auto-join" rather than "a half-join" — ROUND_LOCKED stays
     * the catch-up path for anyone who surfaces later.
     *
     * <p>Both halves are needed: OPEN alone would let a late fire through at round 5 if that
     * round happened to be open, and position 1 alone would let it through once round 1 locked.
     */
    private boolean isFirstRoundStillOpen(Season season) {
        Round currentRound = roundRepo.findById(season.getCurrentRoundId()).orElse(null);
        if (currentRound == null) {
            log.debug("[SEASON_IN_PLAY_SKIPPED] season {} has no resolvable current round", season.getId());
            return false;
        }
        if (currentRound.getPosition() != FIRST_ROUND) {
            log.debug(
                    "[SEASON_IN_PLAY_SKIPPED] season {} is already at round {}",
                    season.getId(),
                    currentRound.getPosition());
            return false;
        }
        // Same call CreatePredictionUseCase.determineAtRoundNumber makes, so the enqueuer and the
        // use case cannot disagree about whether round 1 is still joinable.
        RoundStatus status = roundSupport.resolveJoinEligibilityStatus(currentRound);
        if (status != RoundStatus.OPEN) {
            log.debug("[SEASON_IN_PLAY_SKIPPED] season {} round 1 reads {}", season.getId(), status);
            return false;
        }
        return true;
    }

    private void writeEvent(UUID seasonId) {
        try {
            OutboxEvent event = OutboxEvent.create(
                    "season-in-play:" + seasonId,
                    OutboxEventTypes.SEASON_IN_PLAY,
                    "season",
                    seasonId.toString(),
                    objectMapper.writeValueAsString(new SeasonInPlayPayload(seasonId)));
            if (outboxRepo.save(event)) {
                log.info("[SEASON_IN_PLAY_ENQUEUED] seasonId={}", seasonId);
            } else {
                log.debug("[SEASON_IN_PLAY_SKIPPED] already enqueued for season {}", seasonId);
            }
        } catch (Exception e) {
            log.error("[SEASON_IN_PLAY_ENQUEUE_FAILED] seasonId={}: {}", seasonId, e.getMessage(), e);
        }
    }
}
