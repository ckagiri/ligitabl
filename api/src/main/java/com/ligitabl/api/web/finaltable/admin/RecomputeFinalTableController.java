package com.ligitabl.api.web.finaltable.admin;

import java.util.Map;

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
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>The retry path for {@code FinalTableScoringHook}, which swallows and logs so that a side-game
 * failure cannot block an admin from completing a season.
 */
@Controller
@RequestMapping("/admin/final-table")
@RequiredArgsConstructor
@Slf4j
public class RecomputeFinalTableController {

    private final SeasonRepo seasonRepo;
    private final ScoreFinalTablePredictionsUseCase scoreUseCase;
    private final CompetitionDefaults competitionDefaults;

    @PostMapping("/recompute")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseBody
    public ResponseEntity<?> recompute() {
        Season season = seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .orElseThrow(() -> new IllegalStateException("No active season"));

        log.info("[ADMIN_FINAL_TABLE_RECOMPUTE] season={}", season.getId());

        var summary = scoreUseCase.execute(season, StandingsSource.FINAL_ROUND, true);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "scored", summary.scored(),
                "skipped", summary.skipped(),
                "failed", summary.failed()));
    }
}
