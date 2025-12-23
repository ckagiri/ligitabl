package com.ligitabl.seed.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.model.db.tables.TCompetition;
import com.ligitabl.model.db.tables.TTeam;
import com.ligitabl.seed.testsupport.AbstractSeedPostgresIT;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SeasonSeederValidationIntegrationTest extends AbstractSeedPostgresIT {

    @Autowired DSLContext dsl;
    @Autowired ObjectMapper objectMapper;

    @Test
    void failsFastWhenUsingLegacyTeamsKey() {
        var season = Map.<String, Object>of(
                "clientId", 1,
                "competitionSlug", "any",
                "name", "Any",
                "slug", "any",
                "startDate", "2025-01-01",
                "endDate", "2025-12-31",
                "totalTeams", 2,
                "teams", List.of("AAA", "BBB"),
                "initialRankings", List.of(
                        Map.of("code", "AAA", "position", 1),
                        Map.of("code", "BBB", "position", 2)));

        var seeder = new SeasonSeeder(dsl, objectMapper, List.of());

        assertThatThrownBy(() -> seeder.seed(List.of(season)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("teams")
                .hasMessageContaining("initialRankings");
    }

    @Test
    void failsFastWhenInitialRankingsHasMissingTeamCodeInDatabase() {
        String competitionSlug = "comp-" + UUID.randomUUID();
        insertCompetition(competitionSlug);
        insertTeam("AAA");

        var season = Map.<String, Object>of(
                "clientId", 1,
                "competitionSlug", competitionSlug,
                "name", "Test Season",
                "slug", "s-" + UUID.randomUUID(),
                "startDate", "2025-01-01",
                "endDate", "2025-12-31",
                "totalTeams", 2,
                "initialRankings", List.of(
                        Map.of("code", "AAA", "position", 1),
                        Map.of("code", "ZZZ", "position", 2)));

        var seeder = new SeasonSeeder(dsl, objectMapper, List.of(Map.of("slug", competitionSlug)));

        assertThatThrownBy(() -> seeder.seed(List.of(season)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Teams not found")
                .hasMessageContaining("ZZZ");
    }

    @Test
    void failsFastWhenInitialRankingsPositionsHaveGapOrDuplicates() {
        String competitionSlug = "comp-" + UUID.randomUUID();
        insertCompetition(competitionSlug);
        insertTeam("AAA");
        insertTeam("BBB");
        insertTeam("CCC");

        var season = Map.<String, Object>of(
                "clientId", 1,
                "competitionSlug", competitionSlug,
                "name", "Test Season",
                "slug", "s-" + UUID.randomUUID(),
                "startDate", "2025-01-01",
                "endDate", "2025-12-31",
                "totalTeams", 3,
                "initialRankings", List.of(
                        Map.of("code", "AAA", "position", 1),
                        Map.of("code", "BBB", "position", 1),
                        Map.of("code", "CCC", "position", 3)));

        var seeder = new SeasonSeeder(dsl, objectMapper, List.of(Map.of("slug", competitionSlug)));

        assertThatThrownBy(() -> seeder.seed(List.of(season)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate positions");
    }

    private void insertCompetition(String slug) {
        dsl.insertInto(TCompetition.T_COMPETITION)
                .set(TCompetition.T_COMPETITION.PK_ID, UUID.randomUUID())
                .set(TCompetition.T_COMPETITION.C_NAME, "Competition " + slug)
                .set(TCompetition.T_COMPETITION.C_SLUG, slug)
                .set(TCompetition.T_COMPETITION.C_CODE, "CODE-" + slug.substring(0, Math.min(10, slug.length())))
                .execute();
    }

    private void insertTeam(String tla) {
        String slug = "team-" + tla.toLowerCase() + "-" + UUID.randomUUID();

        dsl.insertInto(TTeam.T_TEAM)
                .set(TTeam.T_TEAM.PK_ID, UUID.randomUUID())
                .set(TTeam.T_TEAM.C_NAME, "Team " + tla)
                .set(TTeam.T_TEAM.C_SHORT_NAME, "Team " + tla)
                .set(TTeam.T_TEAM.C_SLUG, slug)
                .set(TTeam.T_TEAM.C_TLA, tla)
                .execute();
    }
}
