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
import com.ligitabl.model.db.tables.TSeason;
import com.ligitabl.model.domain.Standings;
import com.ligitabl.model.domain.StandingsMetadata;
import com.ligitabl.model.domain.StandingsTeamRank;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.infra.StandingsPersistenceAdapter;

class StandingsRepoTest {

    private static Connection jdbc;
    private static DSLContext dsl;
    private static StandingsRepo repo;

    private static UUID competitionId;
    private static UUID seasonId;

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
        repo = new StandingsPersistenceAdapter(dsl);

        TestDbCleaner.truncatePublicTables(dsl);

        // Minimal competition & season to satisfy FKs
        competitionId = UUID.randomUUID();
        seasonId = UUID.randomUUID();

        dsl.insertInto(TCompetition.T_COMPETITION)
                .set(TCompetition.T_COMPETITION.PK_ID, competitionId)
                .set(TCompetition.T_COMPETITION.C_NAME, "Premier League")
                .set(TCompetition.T_COMPETITION.C_SLUG, "premier-league")
                .set(TCompetition.T_COMPETITION.C_CODE, "PL")
                .set(TCompetition.T_COMPETITION.C_PHASES, JSONB.valueOf("[]"))
                .execute();

        dsl.insertInto(TSeason.T_SEASON)
                .set(TSeason.T_SEASON.PK_ID, seasonId)
                .set(TSeason.T_SEASON.C_CLIENT_ID, 1)
                .set(TSeason.T_SEASON.FK_COMPETITION_ID, competitionId)
                .set(TSeason.T_SEASON.C_NAME, "2024-2025")
                .set(TSeason.T_SEASON.C_SLUG, "2024-2025")
                .set(TSeason.T_SEASON.C_START_DATE, LocalDate.of(2024, 8, 1))
                .set(TSeason.T_SEASON.C_END_DATE, LocalDate.of(2025, 5, 1))
                .set(TSeason.T_SEASON.C_MAX_ROUNDS, 38)
                .set(TSeason.T_SEASON.C_COMPLETED, false)
                .set(TSeason.T_SEASON.C_TOTAL_TEAMS, 0)
                .set(TSeason.T_SEASON.C_MAX_HIT_POINTS, 0)
                .set(TSeason.T_SEASON.C_INITIAL_RANKINGS, (JSONB) null)
                .set(TSeason.T_SEASON.FK_CURRENT_ROUND_ID, (UUID) null)
                .set(TSeason.T_SEASON.C_CURRENT_MATCH_DAY, 0)
                .execute();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (jdbc != null) {
            jdbc.close();
        }
    }

    @Test
    void create_find_update_delete_and_findBySeasonAndRound() {
        List<StandingsTeamRank> rankings = List.of(StandingsTeamRank.builder()
                .ranking(TeamRank.of("ARS", 1))
                .metadata(new StandingsMetadata(1, 1, 0, 0, 3, 2, 0, 2))
                .build());

        Standings toCreate = Standings.builder()
                .seasonId(seasonId)
                .roundPosition(1)
                .rankings(rankings)
                .build();

        Standings created = repo.create(toCreate);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getSeasonId()).isEqualTo(seasonId);
        assertThat(created.getRoundPosition()).isEqualTo(1);
        assertThat(created.getRankings()).hasSize(1);
        assertThat(created.getRankings().get(0).getRanking().getCode()).isEqualTo("ARS");
        assertThat(created.getRankings().get(0).getMetadata().getPoints()).isEqualTo(3);

        Optional<Standings> byId = repo.findById(created.getId());
        assertThat(byId).isPresent();
        assertThat(byId.get().getSeasonId()).isEqualTo(seasonId);

        Optional<Standings> bySeasonRound = repo.findBySeasonAndRoundPosition(seasonId, 1);
        assertThat(bySeasonRound).isPresent();
        assertThat(bySeasonRound.get().getId()).isEqualTo(created.getId());

        List<StandingsTeamRank> updatedRankings = List.of(StandingsTeamRank.builder()
                .ranking(TeamRank.of("ARS", 1))
                .metadata(new StandingsMetadata(2, 2, 0, 0, 6, 4, 0, 4))
                .build());

        Standings toUpdate = Standings.builder()
                .id(created.getId())
                .seasonId(seasonId)
                .roundPosition(2)
                .rankings(updatedRankings)
                .build();

        Standings updated = repo.update(toUpdate);
        assertThat(updated.getRoundPosition()).isEqualTo(2);
        assertThat(updated.getRankings()).hasSize(1);
        assertThat(updated.getRankings().get(0).getMetadata().getPlayed()).isEqualTo(2);
        assertThat(updated.getRankings().get(0).getMetadata().getPoints()).isEqualTo(6);

        repo.delete(updated.getId());
        assertThat(repo.findById(updated.getId())).isEmpty();
        assertThat(repo.findBySeasonAndRoundPosition(seasonId, 2)).isEmpty();
    }
}
