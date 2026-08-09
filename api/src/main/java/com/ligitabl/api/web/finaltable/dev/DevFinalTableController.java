package com.ligitabl.api.web.finaltable.dev;

import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.finaltable.scorefinaltable.ScoreFinalTablePredictionsUseCase;
import com.ligitabl.api.rest.finaltable.scorefinaltable.StandingsSource;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.FinalTablePredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Dev-only trigger for scoring the Final Table against <em>current</em> standings.
 */
@Controller
@Profile("!prod")
@ConditionalOnProperty(name = "ligitabl.final-table.dev-preview.enabled", havingValue = "true")
@RequestMapping("/dev/final-table")
@RequiredArgsConstructor
@Slf4j
public class DevFinalTableController {

    private final SeasonRepo seasonRepo;
    private final FinalTablePredictionRepo predictionRepo;
    private final ScoreFinalTablePredictionsUseCase scoreUseCase;
    private final CompetitionDefaults competitionDefaults;

    /** Scores every row against the latest available standings. Meaningless numbers, real rendering. */
    @PostMapping("/score")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<?> score() {
        Season season = activeSeason();
        log.warn("[DEV_FINAL_TABLE_SCORE] season={} scoring against CURRENT standings", season.getId());

        var summary = scoreUseCase.execute(season, StandingsSource.CURRENT, true);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "scored", summary.scored(),
                "skipped", summary.skipped(),
                "failed", summary.failed(),
                "message", "Scored %d rows against current standings".formatted(summary.scored())));
    }

    /** Returns every row to the waiting state so the trigger can be exercised repeatedly. */
    @PostMapping("/clear")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<?> clear() {
        Season season = activeSeason();
        log.warn("[DEV_FINAL_TABLE_CLEAR] season={} clearing results", season.getId());

        int cleared = predictionRepo.clearResults(season.getId());

        return ResponseEntity.ok(Map.of(
                "success",
                true,
                "cleared",
                cleared,
                "message",
                "Cleared %d rows back to the waiting state".formatted(cleared)));
    }

    private Season activeSeason() {
        return seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .orElseThrow(() -> new IllegalStateException("No active season"));
    }
}
