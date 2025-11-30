package com.ligitabl.model.repo;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamSlug;
import com.ligitabl.model.infra.TeamPersistenceAdapter;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

@Testcontainers
@Tag("integration")
class TeamRepoTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ligitabl")
            .withUsername("ligitabl")
            .withPassword("ligitabl");

    private static Connection jdbc;
    private static DSLContext dsl;
    private static TeamRepo repo;

    @BeforeAll
    static void setup() throws Exception {
        POSTGRES.start();

        // Apply Liquibase changelog from model resources
        try (Connection conn =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            Liquibase liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.yaml",
                    new ClassLoaderResourceAccessor(TeamRepoTest.class.getClassLoader()),
                    new JdbcConnection(conn));
            liquibase.update(new Contexts(), new LabelExpression());
        }

        // Create jOOQ DSLContext
        jdbc = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        dsl = DSL.using(jdbc, SQLDialect.POSTGRES);
        repo = new TeamPersistenceAdapter(dsl);
    }

    @AfterAll
    static void tearDown() {
        if (jdbc != null) {
            try {
                jdbc.close();
            } catch (Exception ignore) {
            }
        }
        POSTGRES.stop();
    }

    @Test
    void create_find_update_delete_flow() {
        // Create
        Team created = repo.create(Team.builder()
                .name("Arsenal FC")
                .shortName("Arsenal")
                .slug(TeamSlug.of("arsenal"))
                .tla("ARS")
                .build());

        assertThat(created.getId()).isNotNull();
        assertThat(created.getCreateDate()).isNotNull();
        assertThat(created.getUpdateDate()).isNotNull();

        UUID id = created.getId();

        // Find by id
        Optional<Team> foundOpt = repo.findById(id);
        assertThat(foundOpt).isPresent();
        Team found = foundOpt.get();
        assertThat(found.getSlug().value()).isEqualTo("arsenal");

        // Update
        OffsetDateTime originalUpdate = found.getUpdateDate();
        Team updated = repo.update(Team.builder()
                .id(id)
                .name("Arsenal Football Club")
                .shortName("Arsenal")
                .slug(TeamSlug.of("arsenal-fc"))
                .tla("ARS")
                .build());
        assertThat(updated.getSlug().value()).isEqualTo("arsenal-fc");
        assertThat(updated.getUpdateDate()).isAfterOrEqualTo(originalUpdate);

        // Exists checks
        assertThat(repo.existsById(id)).isTrue();
        assertThat(repo.existsBySlug(TeamSlug.of("arsenal-fc"))).isTrue();
        assertThat(repo.isSlugInUseByAnotherTeam(TeamSlug.of("arsenal-fc"), id)).isFalse();

        // Delete
        repo.delete(id);
        assertThat(repo.findById(id)).isEmpty();
    }
}
