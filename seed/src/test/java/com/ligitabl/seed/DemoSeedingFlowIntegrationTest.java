package com.ligitabl.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.ligitabl.model.db.tables.TCompetition;
import com.ligitabl.model.db.tables.TSeason;
import com.ligitabl.model.db.tables.TStandings;
import com.ligitabl.seed.testsupport.AbstractSeedPostgresIT;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DemoSeedingFlowIntegrationTest extends AbstractSeedPostgresIT {

    static {
        System.setProperty("seed.main", "seeding/demo-main.yaml");
    }

    @Autowired DSLContext dsl;

    @AfterAll
    static void cleanupSeedMainProperty() {
        System.clearProperty("seed.main");
    }

    @Test
    void demoSeedingCreatesMainContestAndInitialStandings() {
        var competition = dsl.selectFrom(TCompetition.T_COMPETITION)
                .where(TCompetition.T_COMPETITION.C_SLUG.eq("super-premier-league"))
                .fetchAny();
        assertThat(competition).as("demo competition").isNotNull();

        var season = dsl.selectFrom(TSeason.T_SEASON)
                .where(TSeason.T_SEASON.FK_COMPETITION_ID.eq(competition.getId())
                .and(TSeason.T_SEASON.C_SLUG.eq("2024-25")))
                .fetchAny();
        assertThat(season).as("demo season").isNotNull();

        assertThat(season.getMainContestId()).as("season main contest FK").isNotNull();

        int standingsRows = dsl.fetchCount(
                TStandings.T_STANDINGS,
                TStandings.T_STANDINGS.FK_SEASON_ID.eq(season.getId())
                        .and(TStandings.T_STANDINGS.C_ROUND_POSITION.eq(1)));
        assertThat(standingsRows).as("matchday 1 standings row").isEqualTo(1);
    }
}
