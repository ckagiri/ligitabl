package com.ligitabl.model.repo;

import java.util.List;
import java.util.stream.Collectors;

import org.jooq.DSLContext;

final class TestDbCleaner {

    private TestDbCleaner() {}

    static void truncatePublicTables(DSLContext dsl) {
        List<String> tableNames = dsl.fetch("""
                select tablename
                from pg_tables
                where schemaname = 'public'
                  and tablename not in ('databasechangelog', 'databasechangeloglock')
                order by tablename
                """)
                .getValues(0, String.class);

        if (tableNames.isEmpty()) {
            return;
        }

        String tablesSql = tableNames.stream()
                .map(name -> String.format("\"public\".\"%s\"", name))
                .collect(Collectors.joining(", "));

        dsl.execute("TRUNCATE TABLE " + tablesSql + " RESTART IDENTITY CASCADE");
    }
}
