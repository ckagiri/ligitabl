package com.ligitabl.api.runners.calcstandings;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.config.WorkflowConfiguration;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "workflow", name = "run-calcstandings-standings", havingValue = "true")
public class CalcStandingsRunner implements ApplicationRunner {

    private final CalculateRoundStandingsUseCase calculateRoundStandingsUseCase;
    private final HierarchyValidator hierarchyValidator;
    private final CompetitionDefaults competitionDefaults;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final WorkflowConfiguration config;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String competitionSlug = competitionDefaults.defaultCompetitionSlug();

        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║  Calculate Standings Workflow - {}", String.format("%-25s", competitionSlug) + "║");
        log.info("╚══════════════════════════════════════════════════════════╝");

        // Validate competition and get active season
        var competitionResult = hierarchyValidator.validateCompetition(competitionSlug);
        if (competitionResult.isLeft()) {
            handleError("Competition validation failed", competitionResult.getLeft());
            return;
        }

        Competition competition = competitionResult.get();

        if (competition.getActiveSeasonId() == null) {
            log.error("Competition {} has no active season", competitionSlug);
            if (config.isExitAfter()) {
                System.exit(1);
            }
            return;
        }

        var seasonResult = seasonRepo.findById(competition.getActiveSeasonId());
        if (seasonResult.isEmpty()) {
            log.error("Active season not found for competition {}", competitionSlug);
            if (config.isExitAfter()) {
                System.exit(1);
            }
            return;
        }

        Season season = seasonResult.get();

        // Get all rounds for the season, ordered by position
        List<Round> rounds = roundRepo.findBySeasonIdOrderByPosition(season.getId());

        if (rounds.isEmpty()) {
            log.warn("No rounds found for season {}", season.getSlug());
            if (config.isExitAfter()) {
                System.exit(0);
            }
            return;
        }

        log.info("Found {} rounds for season {}", rounds.size(), season.getSlug());
        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║  Processing Rounds                                       ║");
        log.info("╚══════════════════════════════════════════════════════════╝");

        // Calculate standings for each round
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicInteger skippedCount = new AtomicInteger(0);

        for (Round round : rounds) {
            var command = CalculateRoundStandingsCommand.byPosition(round.getPosition(), competitionSlug);
            var result = calculateRoundStandingsUseCase.execute(command);

            if (result == null) {
                log.warn("Round {} - Failed: CalculateRoundStandingsUseCase returned null", round.getPosition());
                failureCount.incrementAndGet();
                continue;
            }

            result.fold(
                    error -> {
                        // Check if error is because standings are already finalized
                        if (error.getMessage().contains("already finalised")
                                || error.getMessage().contains("No finished matches in round")) {
                            log.debug("Round {} - Skipped (already finalized)", round.getPosition());
                            skippedCount.incrementAndGet();
                        } else {
                            log.warn("Round {} - Failed: {}", round.getPosition(), error.getMessage());
                            failureCount.incrementAndGet();
                        }
                        return null;
                    },
                    standings -> {
                        log.info("Round {} - Success ({} teams)", round.getPosition(), standings.size());
                        successCount.incrementAndGet();
                        return null;
                    });
        }

        // Print summary
        log.info("╔══════════════════════════════════════════════════════════╗");
        log.info("║  Calculation Summary                                     ║");
        log.info("╠══════════════════════════════════════════════════════════╣");
        log.info("║  Competition:  {}", String.format("%-44s", competitionSlug) + "║");
        log.info("║  Season:       {}", String.format("%-44s", season.getSlug()) + "║");
        log.info("║  Total Rounds: {}", String.format("%-44s", rounds.size()) + "║");
        log.info("║  Calculated:   {}", String.format("%-44s", successCount.get()) + "║");
        log.info("║  Skipped:      {}", String.format("%-44s", skippedCount.get()) + "║");
        log.info("║  Failed:       {}", String.format("%-44s", failureCount.get()) + "║");
        log.info("╠══════════════════════════════════════════════════════════╣");

        if (failureCount.get() == 0) {
            log.info("║  Status:       ✅ SUCCESS                                 ║");
        } else if (successCount.get() > 0) {
            log.warn("║  Status:       ⚠️  PARTIAL SUCCESS                        ║");
        } else {
            log.error("║  Status:       ❌ FAILED                                  ║");
        }

        log.info("╚══════════════════════════════════════════════════════════╝");

        if (config.isExitAfter()) {
            int exitCode = failureCount.get() == 0 ? 0 : (successCount.get() > 0 ? 2 : 1);
            System.exit(exitCode);
        }
    }

    private void handleError(String context, UseCaseError error) {
        log.error("╔══════════════════════════════════════════════════════════╗");
        log.error("║  Calculation FAILED                                      ║");
        log.error("╚══════════════════════════════════════════════════════════╝");
        log.error("Context:       {}", context);
        // log.error("Error Code:    {}", error.code());
        log.error("Error Message: {}", error.getMessage());

        if (config.isExitAfter()) {
            System.exit(1);
        }
    }
}
