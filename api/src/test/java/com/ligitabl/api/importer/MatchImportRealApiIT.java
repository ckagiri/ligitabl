package com.ligitabl.api.importer;

import com.ligitabl.api.LigitablApplication;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
@EnabledIfEnvironmentVariable(named = "FOOTBALL_DATA_API_KEY", matches = ".+")
class MatchImportRealApiIT {

    @Autowired
    private MatchImportService matchImportService;

    @Test
    void importsPremierLeagueMatchesFromRealApi() {
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
}
