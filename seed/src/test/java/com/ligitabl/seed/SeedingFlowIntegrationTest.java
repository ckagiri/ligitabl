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

@SpringBootTest(properties = "SEED_RUN_LIQUIBASE=true")
@ActiveProfiles("test")
class SeedingFlowIntegrationTest {

    @Autowired DSLContext dsl;

    @Test
    void seedingPopulatesCompetitionSeasonRoundAndDefaults() {
        var competition =
                dsl.fetchOne(
                        TCompetition.T_COMPETITION,
                        TCompetition.T_COMPETITION.C_SLUG.eq("premier-league"));
        assertThat(competition).as("premier-league competition").isNotNull();

        var season =
                dsl.fetchOne(
                        TSeason.T_SEASON,
                        TSeason.T_SEASON.FK_COMPETITION_ID.eq(competition.getPkId()));
        assertThat(season).as("season for premier-league").isNotNull();

        var rounds =
                dsl.fetchCount(
                        TRound.T_ROUND,
                        TRound.T_ROUND.FK_SEASON_ID.eq(season.getPkId()));
        assertThat(rounds).as("rounds for season").isGreaterThan(0);

        assertThat(competition.getFkActiveSeasonId())
                .as("competition active season FK")
                .isNotNull();
        assertThat(season.getFkCurrentRoundId()).as("season current round FK").isNotNull();
    }
}
