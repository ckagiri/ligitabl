package com.ligitabl.api.runners.demoseeding;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ligitabl.api.shared.Either;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Command line runner for seeding demo data.
 *
 * Usage: ./mvnw spring-boot:run -Dspring-boot.run.arguments=--seed-season
 * Or:    java -jar app.jar --seed-season
 *
 * Note: the app should typically be started with:
 *   --spring.main.web-application-type=none
 * so the process exits after seeding.
 */
@Component
@ConditionalOnProperty(name = "seed-season")
@RequiredArgsConstructor
@Slf4j
public class SeedSeasonCommandLineRunner implements CommandLineRunner {

    private final SeedSeasonUseCase seedSeasonUseCase;

    @Override
    public void run(String... args) {
        log.info("═══════════════════════════════════════════════════════════");
        log.info("  SEASON SEEDING STARTED");
        log.info("═══════════════════════════════════════════════════════════");
        log.info("");

        Either<SeedingError, SeasonSeedResult> result = seedSeasonUseCase.execute();

        result.fold(this::handleFailure, this::handleSuccess);
    }

    private Void handleFailure(SeedingError error) {
        log.error("");
        log.error("═══════════════════════════════════════════════════════════");
        log.error("  ❌ SEEDING FAILED");
        log.error("═══════════════════════════════════════════════════════════");
        log.error("");
        log.error("Error: {}", error.message());
        log.error("");
        log.error("Prerequisites checklist:");
        log.error("  ☐ Competition exists (check seeding-config.yaml)");
        log.error("  ☐ Season exists with initial_rankings");
        log.error("  ☐ Rounds created (max_rounds rounds)");
        log.error("  ☐ All teams exist in database");
        log.error("  ☐ Default contest exists");
        log.error("  ☐ Round 1 standings created (auto-trigger)");
        log.error("");
        log.error("Please create missing entities and try again.");
        log.error("");

        System.exit(1);
        return null;
    }

    private Void handleSuccess(SeasonSeedResult result) {
        log.info("");
        log.info("═══════════════════════════════════════════════════════════");
        log.info("  ✅ SEEDING COMPLETED SUCCESSFULLY");
        log.info("═══════════════════════════════════════════════════════════");
        log.info("");
        log.info("📊 Summary:");
        log.info("  • Season: {}", result.getSeason().getName());
        log.info("  • Contest: {}", result.getDefaultContest().getName());
        log.info("  • Rounds: {}", result.getTotalRounds());
        log.info("  • Matches seeded: {}", result.getMatchesSeeded());
        log.info("  • Users: {}", result.getUsers().size());
        log.info("  • Predictions created: {}", result.getPredictions().size());
        log.info("  • Swaps generated: {}", result.getSwapsSeeded());
        log.info("  • Rounds finalized: {}", result.getRoundsFinalized());
        log.info("");

        if (!result.getWarnings().isEmpty()) {
            log.warn("⚠️  Warnings:");
            result.getWarnings().forEach(warning -> log.warn("  • {}", warning));
            log.info("");
        }

        log.info("🎮 Demo Users:");
        result.getUsers().forEach(user -> log.info("  • {} ({})", user.getDisplayName(), user.getEmail()));
        log.info("  Password: Demo123!");
        log.info("");

        log.info("🚀 Application ready with demo data!");
        log.info("");

        return null;
    }
}
