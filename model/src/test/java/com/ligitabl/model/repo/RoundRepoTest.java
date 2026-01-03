package com.ligitabl.model.repo;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.ligitabl.model.db.tables.TCompetition;
import com.ligitabl.model.db.tables.TRound;
import com.ligitabl.model.db.tables.TSeason;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.infra.RoundPersistenceAdapter;

class RoundRepoTest {

    private static Connection jdbc;
    private static DSLContext dsl;
    private static RoundRepo repo;

    @BeforeAll
    static void setup() throws Exception {
        String host = System.getenv().getOrDefault("DB_HOST", "localhost");
        String port = System.getenv().getOrDefault("DB_PORT", "55433");
        String db = System.getenv().getOrDefault("DB_NAME", "ligitabl");
        String user = System.getenv().getOrDefault("DB_USER", "ligitabl");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "ligitabl");

        String url = String.format("jdbc:postgresql://%s:%s/%s", host, port, db);
        jdbc = DriverManager.getConnection(url, user, password);
        dsl = DSL.using(jdbc, SQLDialect.POSTGRES);
        repo = new RoundPersistenceAdapter(dsl);

        TestDbCleaner.truncatePublicTables(dsl);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (jdbc != null) {
            jdbc.close();
        }
    }

    @Test
    void findBySeasonId_and_findBySeasonIdAndPosition() {
        UUID competitionId = UUID.randomUUID();
        UUID seasonId = UUID.randomUUID();
        UUID round1Id = UUID.randomUUID();
        UUID round2Id = UUID.randomUUID();

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
                .execute();

        dsl.insertInto(TRound.T_ROUND)
                .set(TRound.T_ROUND.PK_ID, round1Id)
                .set(TRound.T_ROUND.FK_SEASON_ID, seasonId)
                .set(TRound.T_ROUND.C_NAME, "Round 1")
                .set(TRound.T_ROUND.C_SLUG, "round-1")
                .set(TRound.T_ROUND.C_POSITION, 1)
                .execute();

        dsl.insertInto(TRound.T_ROUND)
                .set(TRound.T_ROUND.PK_ID, round2Id)
                .set(TRound.T_ROUND.FK_SEASON_ID, seasonId)
                .set(TRound.T_ROUND.C_NAME, "Round 2")
                .set(TRound.T_ROUND.C_SLUG, "round-2")
                .set(TRound.T_ROUND.C_POSITION, 2)
                .execute();

        List<Round> rounds = repo.findBySeasonId(seasonId);
        assertThat(rounds).hasSize(2);
        assertThat(rounds.get(0).getPosition()).isEqualTo(1);
        assertThat(rounds.get(1).getPosition()).isEqualTo(2);

        Optional<Round> round1 = repo.findBySeasonIdAndPosition(seasonId, 1);
        assertThat(round1).isPresent();
        assertThat(round1.get().getId()).isEqualTo(round1Id);
    }
}
