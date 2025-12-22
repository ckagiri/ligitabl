package com.ligitabl.api.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

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

        registry.add("jwt.secret", () -> "test-secret-change-me-test-secret-change-me-32bytes-min");
        registry.add("jwt.expiration", () -> 86_400_000L);
    }
}
