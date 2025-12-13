package com.ligitabl.model.repo;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.ligitabl.model.db.tables.TCompetition;
import com.ligitabl.model.db.tables.TMatch;
import com.ligitabl.model.db.tables.TRound;
import com.ligitabl.model.db.tables.TSeason;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.infra.SeasonPersistenceAdapter;

class SeasonRepoTest {

    private static Connection jdbc;
    private static DSLContext dsl;
    private static SeasonRepo repo;

    @BeforeAll
    static void setup() throws Exception {
        String host = System.getenv().getOrDefault("DB_HOST", "localhost");
        String port = System.getenv().getOrDefault("DB_PORT", "55432");
        String db = System.getenv().getOrDefault("DB_NAME", "ligitabl");
        String user = System.getenv().getOrDefault("DB_USER", "ligitabl");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "ligitabl");

        String url = String.format("jdbc:postgresql://%s:%s/%s", host, port, db);
        jdbc = DriverManager.getConnection(url, user, password);
        dsl = DSL.using(jdbc, SQLDialect.POSTGRES);
        repo = new SeasonPersistenceAdapter(dsl);

        // Clean slate (respect FK order)
        dsl.deleteFrom(TMatch.T_MATCH).execute();
        dsl.deleteFrom(TRound.T_ROUND).execute();
        dsl.deleteFrom(TSeason.T_SEASON).execute();
        dsl.deleteFrom(TCompetition.T_COMPETITION).execute();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (jdbc != null) {
            jdbc.close();
        }
    }

    @Test
    void findAllByCompetitionId_and_findByCompetitionIdAndSlug_and_existsById() {
        UUID competitionId = UUID.randomUUID();
        UUID seasonId = UUID.randomUUID();

        dsl.insertInto(TCompetition.T_COMPETITION)
                .set(TCompetition.T_COMPETITION.PK_ID, competitionId)
                .set(TCompetition.T_COMPETITION.C_NAME, "Premier League")
                .set(TCompetition.T_COMPETITION.C_SLUG, "premier-league")
                .set(TCompetition.T_COMPETITION.C_CODE, "PL")
                .execute();

        dsl.insertInto(TSeason.T_SEASON)
                .set(TSeason.T_SEASON.PK_ID, seasonId)
                .set(TSeason.T_SEASON.C_CLIENT_ID, 1)
                .set(TSeason.T_SEASON.FK_COMPETITION_ID, competitionId)
                .set(TSeason.T_SEASON.C_NAME, "2024/25")
                .set(TSeason.T_SEASON.C_SLUG, "2024-25")
                .set(TSeason.T_SEASON.C_START_DATE, LocalDate.of(2024, 8, 1))
                .set(TSeason.T_SEASON.C_END_DATE, LocalDate.of(2025, 5, 31))
                .set(TSeason.T_SEASON.C_MAX_ROUNDS, 38)
                .set(TSeason.T_SEASON.C_CURRENT_MATCH_DAY, 0)
                .set(
                        TSeason.T_SEASON.C_TEAMS,
                        JSONB.valueOf("[{\"code\":\"ARS\",\"position\":1},{\"code\":\"MCI\",\"position\":2}]"))
                .execute();

        List<Season> byCompetition = repo.findAllByCompetitionId(competitionId);
        assertThat(byCompetition).hasSize(1);

        Optional<Season> bySlug = repo.findByCompetitionIdAndSlug(competitionId, SeasonSlug.of("2024-25"));
        assertThat(bySlug).isPresent();
        assertThat(bySlug.get().getId()).isEqualTo(seasonId);

        assertThat(bySlug.get().getTeams()).hasSize(2).satisfies(teams -> {
            assertThat(teams.get(0).getCode()).isEqualTo("ARS");
            assertThat(teams.get(0).getPosition()).isEqualTo(1);
            assertThat(teams.get(1).getCode()).isEqualTo("MCI");
            assertThat(teams.get(1).getPosition()).isEqualTo(2);
        });

        assertThat(repo.existsById(seasonId)).isTrue();
    }
}
