package com.ligitabl.model.repo;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
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
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.infra.CompetitionPersistenceAdapter;

class CompetitionRepoTest {

    private static Connection jdbc;
    private static DSLContext dsl;
    private static CompetitionRepo repo;

    @BeforeAll
    static void setup() throws Exception {
        String host = System.getenv().getOrDefault("DB_HOST", "localhost");
        String port = System.getenv().getOrDefault("DB_PORT", "55433");
        String db = System.getenv().getOrDefault("DB_NAME", "ligitabl_test");
        String user = System.getenv().getOrDefault("DB_USER", "ligitabl");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "ligitabl");

        String url = String.format("jdbc:postgresql://%s:%s/%s", host, port, db);
        jdbc = DriverManager.getConnection(url, user, password);
        dsl = DSL.using(jdbc, SQLDialect.POSTGRES);
        repo = new CompetitionPersistenceAdapter(dsl);

        TestDbCleaner.truncatePublicTables(dsl);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (jdbc != null) {
            jdbc.close();
        }
    }

    @Test
    void findAll_and_findBySlug_and_existsById() {
        UUID id = UUID.randomUUID();

        dsl.insertInto(TCompetition.T_COMPETITION)
                .set(TCompetition.T_COMPETITION.PK_ID, id)
                .set(TCompetition.T_COMPETITION.C_NAME, "Premier League")
                .set(TCompetition.T_COMPETITION.C_SLUG, "premier-league")
                .set(TCompetition.T_COMPETITION.C_CODE, "PL")
                .set(
                        TCompetition.T_COMPETITION.C_PHASES,
                        JSONB.valueOf("[{\"from\":1,\"to\":9},{\"from\":10,\"to\":19}]"))
                .execute();

        List<Competition> all = repo.findAll();
        assertThat(all).hasSize(1);
        Competition competition = all.get(0);
        assertThat(competition.getName()).isEqualTo("Premier League");

        Optional<Competition> bySlug = repo.findBySlug(CompetitionSlug.of("premier-league"));
        assertThat(bySlug).isPresent();
        assertThat(bySlug.get().getId()).isEqualTo(id);

        assertThat(bySlug.get().getPhases()).hasSize(2).satisfies(phases -> {
            assertThat(phases.get(0).getFrom()).isEqualTo(1);
            assertThat(phases.get(0).getTo()).isEqualTo(9);
            assertThat(phases.get(1).getFrom()).isEqualTo(10);
            assertThat(phases.get(1).getTo()).isEqualTo(19);
        });

        assertThat(repo.existsById(id)).isTrue();
    }
}
