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

        // Every distinct Spring test context opens its own pool against this one container, and the
        // contexts are cached rather than closed — so total connections grow with the number of
        // *distinct* context configurations, not with the number of tests running. At Hikari's
        // default of 10 the suite reached "FATAL: sorry, too many clients already" against
        // postgres's default max_connections of 100. Tests run single-threaded within a context, so
        // a pool this size is ample; capping it is what keeps adding a test configuration from
        // being a suite-wide hazard.
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 3);
        registry.add("spring.datasource.hikari.minimum-idle", () -> 0);

        registry.add("jwt.secret", () -> "test-secret-change-me-test-secret-change-me-32bytes-min");
        registry.add("jwt.expiration", () -> 86_400_000L);
    }
}
