package com.ligitabl.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.ligitabl.model.db.tables.TCompetition;
import com.ligitabl.model.db.tables.TRound;
import com.ligitabl.model.db.tables.TSeason;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.ligitabl.seed.testsupport.AbstractSeedPostgresIT;

@SpringBootTest
@ActiveProfiles("test")
class SeedingFlowIntegrationTest extends AbstractSeedPostgresIT {

        static {
                // Force production/reference seeding so the premier-league assertions are deterministic.
                System.setProperty("seed.main", "seeding/main.yaml");
        }

    @Autowired DSLContext dsl;

        @org.junit.jupiter.api.AfterAll
        static void cleanupSeedMainProperty() {
                System.clearProperty("seed.main");
        }

    @Test
    void seedingPopulatesCompetitionSeasonRoundAndDefaults() {
        var competition =
                dsl.selectFrom(TCompetition.T_COMPETITION)
                        .where(TCompetition.T_COMPETITION.C_SLUG.eq("premier-league"))
                        .orderBy(TCompetition.T_COMPETITION.PK_ID.asc())
                        .fetchAny();
        assertThat(competition).as("premier-league competition").isNotNull();

        var season =
                dsl.selectFrom(TSeason.T_SEASON)
                        .where(
                                TSeason.T_SEASON.FK_COMPETITION_ID.eq(competition.getId())
                                        .and(TSeason.T_SEASON.C_SLUG.eq("2025-26")))
                        .orderBy(TSeason.T_SEASON.PK_ID.asc())
                        .fetchAny();
        assertThat(season).as("season for premier-league").isNotNull();

        var rounds =
                dsl.fetchCount(
                        TRound.T_ROUND,
                        TRound.T_ROUND.FK_SEASON_ID.eq(season.getId()));
        assertThat(rounds).as("rounds for season").isGreaterThan(0);

        assertThat(competition.getActiveSeasonId())
                .as("competition active season FK")
                .isNotNull();
        assertThat(season.getCurrentRoundId()).as("season current round FK").isNotNull();
    }
}
