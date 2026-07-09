package com.ligitabl.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.ligitabl.model.db.tables.TCompetition;
import com.ligitabl.model.db.tables.TContest;
import com.ligitabl.model.db.tables.TRound;
import com.ligitabl.model.db.tables.TSeason;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;

import com.ligitabl.seed.testsupport.AbstractSeedPostgresIT;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
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

        // seeding/round.yaml seeds rounds for both 2025-26 and 2026-27; assert both exist,
        // independent of which one the defaults resolver picks as "current".
        for (String seasonSlug : new String[] {"2025-26", "2026-27"}) {
            var season =
                    dsl.selectFrom(TSeason.T_SEASON)
                            .where(
                                    TSeason.T_SEASON.FK_COMPETITION_ID.eq(competition.getId())
                                            .and(TSeason.T_SEASON.C_SLUG.eq(seasonSlug)))
                            .orderBy(TSeason.T_SEASON.PK_ID.asc())
                            .fetchAny();
            assertThat(season).as("season " + seasonSlug + " for premier-league").isNotNull();

            var rounds =
                    dsl.fetchCount(TRound.T_ROUND, TRound.T_ROUND.FK_SEASON_ID.eq(season.getId()));
            assertThat(rounds).as("rounds for season " + seasonSlug).isGreaterThan(0);
        }

        // defaults.yaml has no seasonSlug, so CurrentSeasonResolver picks whichever season is
        // current/upcoming relative to "now" and wires the FKs onto that one.
        assertThat(competition.getActiveSeasonId())
                .as("competition active season FK")
                .isNotNull();

        var currentSeason =
                dsl.selectFrom(TSeason.T_SEASON)
                        .where(TSeason.T_SEASON.PK_ID.eq(competition.getActiveSeasonId()))
                        .fetchOne();
        assertThat(currentSeason).as("resolved current season").isNotNull();
        assertThat(currentSeason.getCurrentRoundId()).as("season current round FK").isNotNull();
        assertThat(currentSeason.getMainContestId()).as("season main contest FK").isNotNull();

        var contest =
                dsl.fetchCount(
                        TContest.T_CONTEST, TContest.T_CONTEST.FK_SEASON_ID.eq(currentSeason.getId()));
        assertThat(contest).as("main contest row for resolved current season").isEqualTo(1);
    }
}
