package com.ligitabl.api.testsupport;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresTestDbCleaner {

    private PostgresTestDbCleaner() {
    }

    /**
     * Truncates all "domain" tables (currently all tables in schema {@code public} whose names start
     * with {@code t_}). Uses {@code CASCADE} to avoid manual FK ordering.
     */
    public static void truncateAllDomainTables(JdbcTemplate jdbcTemplate) {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename LIKE 't\\_%' ESCAPE '\\'",
                String.class);

        if (tables.isEmpty()) {
            return;
        }

        tables.sort(Comparator.naturalOrder());

        String tableList = tables.stream()
                .map(PostgresTestDbCleaner::qualifiedAndQuoted)
                .collect(Collectors.joining(", "));

        jdbcTemplate.execute("TRUNCATE TABLE " + tableList + " RESTART IDENTITY CASCADE");
    }

    private static String qualifiedAndQuoted(String tableName) {
        // Defensive quoting for unusual identifiers.
        String escaped = tableName.replace("\"", "\"\"");
        return "\"public\".\"" + escaped + "\"";
    }
}
