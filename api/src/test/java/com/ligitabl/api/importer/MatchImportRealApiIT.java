package com.ligitabl.api.importer;

import com.ligitabl.api.LigitablApplication;
import com.ligitabl.api.testsupport.PostgresContainerConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Real integration test that hits the external football-data.org API.
 *
 * Preconditions:
 * - FOOTBALL_DATA_API_KEY must be set in the environment.
 * - The database must already be migrated and seeded such that the
 *   external season id for PL maps to a Season.clientId in Ligitabl.
 *
 * This test does NOT manage the database lifecycle; it is intended to be
 * run manually when verifying the real import pipeline end to end.
 */
@SpringBootTest(classes = LigitablApplication.class)
@Import(PostgresContainerConfig.class)
@EnabledIfEnvironmentVariable(named = "FOOTBALL_DATA_API_KEY", matches = ".+")
class MatchImportRealApiIT {

    @Autowired
    private MatchImportService matchImportService;

        @Autowired
        private DataSource dataSource;

    @Test
    void importsPremierLeagueMatchesFromRealApi() {
                seedDatabaseWithReferenceData();

        MatchImportService.ImportResult result =
                matchImportService.importMatchesForCompetition("PL");

        Assertions.assertTrue(result.isSuccess(),
                "Expected import to succeed, but got: " + result.getMessage());

        // At this stage, `created` reflects the number of matches fetched
        // from the external API. For the Premier League this should be 380.
        Assertions.assertEquals(380, result.getCreated(),
                "Expected 380 matches for PL current season");

        Assertions.assertNotNull(result.getSeasonName(),
                "Season name should be resolved via SeasonRepo");
    }

        private void seedDatabaseWithReferenceData() {
                String jdbcUrl = resolveJdbcUrl();
                Path seedJarPath = resolveSeedJarPath();

                ProcessBuilder processBuilder = new ProcessBuilder(
                                "java",
                                "-jar",
                                seedJarPath.toAbsolutePath().toString());

                processBuilder.environment().put("DB_URL", jdbcUrl);
                processBuilder.environment().put("APP_ENV", "production");

                processBuilder.inheritIO();

                try {
                        Process process = processBuilder.start();
                        int exitCode = process.waitFor();
                        Assertions.assertEquals(0, exitCode, "Seeding process failed with exit code " + exitCode);
                } catch (IOException | InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Failed to run seed JAR", e);
                }
        }

        private String resolveJdbcUrl() {
                try {
                        return dataSource.getConnection().getMetaData().getURL();
                } catch (Exception e) {
                        throw new IllegalStateException("Failed to resolve JDBC URL from DataSource", e);
                }
        }

        private Path resolveSeedJarPath() {
                // Tests run from the api module; the seed JAR is built in ../seed/target
                Path apiModuleDir = Paths.get("").toAbsolutePath();
                Path seedJar = apiModuleDir.getParent()
                                .resolve("seed")
                                .resolve("target")
                                .resolve("ligitabl-seed-0.1.0-SNAPSHOT.jar");

                if (!Files.isRegularFile(seedJar)) {
                        throw new IllegalStateException("Seed JAR not found at " + seedJar.toAbsolutePath());
                }

                return seedJar;
        }
}
