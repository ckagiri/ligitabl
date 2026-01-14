package com.ligitabl.seed.testsupport;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class AbstractSeedPostgresIT {

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ligitabl")
            .withUsername("ligitabl")
            .withPassword("ligitabl")
            .withUrlParam("sslmode", "disable");

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (POSTGRES.isRunning()) {
                POSTGRES.stop();
            }
        }));
    }

    @BeforeAll
    static void startContainer() {
        ensureStarted();
    }

    private static void ensureStarted() {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        ensureStarted();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

        // Seed module disables Liquibase by default; enable it for ITs so schema is created.
        registry.add("spring.liquibase.enabled", () -> true);
    }
}
