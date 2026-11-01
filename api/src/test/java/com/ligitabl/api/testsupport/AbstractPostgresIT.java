package com.ligitabl.api.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SuppressWarnings("resource")
public abstract class AbstractPostgresIT {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ligitabl")
            .withUsername("ligitabl")
            .withPassword("ligitabl");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.liquibase.enabled", () -> true);

        // Spring caches a context per distinct configuration, each with its own pool against this
        // one container, so connections grow with configurations, not tests. Hikari's default
        // exhausts postgres's max_connections; tests run single-threaded per context, so this is ample.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 3);
        registry.add("spring.datasource.hikari.minimum-idle", () -> 0);

        registry.add("jwt.secret", () -> "test-secret-change-me-test-secret-change-me-32bytes-min");
        registry.add("jwt.expiration", () -> 86_400_000L);
    }
}
