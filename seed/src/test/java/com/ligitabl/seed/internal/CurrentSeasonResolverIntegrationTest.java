package com.ligitabl.seed.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ligitabl.model.db.tables.TCompetition;
import com.ligitabl.model.db.tables.TSeason;
import com.ligitabl.seed.testsupport.AbstractSeedPostgresIT;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CurrentSeasonResolverIntegrationTest extends AbstractSeedPostgresIT {

    static {
        // SeedingApplication's CommandLineRunner requires this system property to start.
        System.setProperty("seed.main", "seeding/main.yaml");
    }

    @Autowired DSLContext dsl;

    @Test
    void picksSeasonWhoseDateRangeContainsToday() {
        String competitionSlug = insertCompetition();
        insertSeason(competitionSlug, "past", LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31));
        insertSeason(competitionSlug, "active", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
        insertSeason(competitionSlug, "future", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        CurrentSeasonResolver resolver = new CurrentSeasonResolver(dsl, clockOn(2025, 6, 15));

        assertThat(resolver.resolveCurrentSeasonSlug(competitionSlug)).isEqualTo("active");
    }

    @Test
    void picksSoonestUpcomingSeasonWhenNoneIsActive() {
        String competitionSlug = insertCompetition();
        insertSeason(competitionSlug, "ended", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
        insertSeason(competitionSlug, "soonest-upcoming", LocalDate.of(2026, 8, 1), LocalDate.of(2027, 5, 31));
        insertSeason(competitionSlug, "later-upcoming", LocalDate.of(2027, 8, 1), LocalDate.of(2028, 5, 31));

        // Between seasons: 2024 has ended, 2026-08 hasn't started yet.
        CurrentSeasonResolver resolver = new CurrentSeasonResolver(dsl, clockOn(2026, 7, 9));

        assertThat(resolver.resolveCurrentSeasonSlug(competitionSlug)).isEqualTo("soonest-upcoming");
    }

    @Test
    void fallsBackToMostRecentlyEndedSeasonWhenNoneIsActiveOrUpcoming() {
        String competitionSlug = insertCompetition();
        insertSeason(competitionSlug, "older", LocalDate.of(2023, 1, 1), LocalDate.of(2023, 12, 31));
        insertSeason(competitionSlug, "most-recent", LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));

        CurrentSeasonResolver resolver = new CurrentSeasonResolver(dsl, clockOn(2026, 7, 9));

        assertThat(resolver.resolveCurrentSeasonSlug(competitionSlug)).isEqualTo("most-recent");
    }

    @Test
    void throwsWhenCompetitionHasNoSeasons() {
        String competitionSlug = insertCompetition();

        CurrentSeasonResolver resolver = new CurrentSeasonResolver(dsl, clockOn(2026, 7, 9));

        assertThatThrownBy(() -> resolver.resolveCurrentSeasonSlug(competitionSlug))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no seasons found");
    }

    private static Clock clockOn(int year, int month, int day) {
        return Clock.fixed(
                LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
    }

    private String insertCompetition() {
        String slug = "comp-" + UUID.randomUUID();
        dsl.insertInto(TCompetition.T_COMPETITION)
                .set(TCompetition.T_COMPETITION.PK_ID, UUID.randomUUID())
                .set(TCompetition.T_COMPETITION.C_NAME, "Competition " + slug)
                .set(TCompetition.T_COMPETITION.C_SLUG, slug)
                .set(TCompetition.T_COMPETITION.C_CODE, "CODE-" + slug.substring(0, Math.min(10, slug.length())))
                .execute();
        return slug;
    }

    private void insertSeason(String competitionSlug, String slugSuffix, LocalDate start, LocalDate end) {
        UUID competitionId = dsl.select(TCompetition.T_COMPETITION.PK_ID)
                .from(TCompetition.T_COMPETITION)
                .where(TCompetition.T_COMPETITION.C_SLUG.eq(competitionSlug))
                .fetchOne(TCompetition.T_COMPETITION.PK_ID);

        dsl.insertInto(TSeason.T_SEASON)
                .set(TSeason.T_SEASON.PK_ID, UUID.randomUUID())
                .set(TSeason.T_SEASON.C_CLIENT_ID, 1)
                .set(TSeason.T_SEASON.FK_COMPETITION_ID, competitionId)
                .set(TSeason.T_SEASON.C_NAME, "Season " + slugSuffix)
                .set(TSeason.T_SEASON.C_SLUG, slugSuffix)
                .set(TSeason.T_SEASON.C_START_DATE, start)
                .set(TSeason.T_SEASON.C_END_DATE, end)
                .set(TSeason.T_SEASON.C_MAX_ROUNDS, 38)
                .set(TSeason.T_SEASON.C_CURRENT_MATCH_DAY, 1)
                .execute();
    }
}
