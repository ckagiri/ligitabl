package com.ligitabl.api.rest.finaltable.scorefinaltable;

import org.springframework.stereotype.Component;

import com.ligitabl.model.domain.Season;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scores the Final Table side game when an admin completes a season.
 *
 * <p>Hangs off {@code CompleteSeasonUseCase} rather than round finalization: that use case is
 * re-runnable and scheduler-triggered, so "last round finalized" happens repeatedly and is not a
 * season-end fact. Completion is the single explicit, admin-triggered, transactional end of season.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FinalTableScoringHook {

    private final ScoreFinalTablePredictionsUseCase scoreUseCase;

    /**
     * Runs inside the completing transaction, so the result is there the moment the season is.
     *
     * <p>Swallows and logs, matching {@code enqueueSeasonCompleted} beside it and for the same
     * reason: a side-game failure must never block an admin from completing the season. The admin
     * recompute endpoint is the retry path.
     */
    public void onSeasonCompleted(Season season) {
        try {
            // FINAL_ROUND explicitly: the enum has no default, and CURRENT must never run from here.
            var summary = scoreUseCase.execute(season, StandingsSource.FINAL_ROUND, false);
            log.info(
                    "[FINAL_TABLE_SEASON_COMPLETED] season={} scored={} skipped={} failed={}",
                    season.getId(),
                    summary.scored(),
                    summary.skipped(),
                    summary.failed());
        } catch (Exception e) {
            log.error("[FINAL_TABLE_SCORING_FAILED] season={}: {}", season.getId(), e.getMessage(), e);
        }
    }
}
