package com.ligitabl.model.repo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Opens connections to the shared integration-test database (the compose DB,
 * DB_* env vars with ENV=test defaults). Not for Testcontainers-based tests,
 * which manage their own container and URL.
 */
final class TestDbConnections {

    private TestDbConnections() {}

    static Connection open() throws SQLException {
        String host = System.getenv().getOrDefault("DB_HOST", "localhost");
        String port = System.getenv().getOrDefault("DB_PORT", "55433");
        String db = System.getenv().getOrDefault("DB_NAME", "ligitabl_test");
        String user = System.getenv().getOrDefault("DB_USER", "ligitabl");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "ligitabl");

        String url = String.format("jdbc:postgresql://%s:%s/%s", host, port, db);
        return DriverManager.getConnection(url, user, password);
    }
}
