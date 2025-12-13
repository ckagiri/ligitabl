package com.ligitabl.seed.internal;

import static com.ligitabl.model.db.tables.TCompetition.T_COMPETITION;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;

public class CompetitionSeeder {

    private final DSLContext dsl;

    public CompetitionSeeder(DSLContext dsl) {
        this.dsl = dsl;
    }

    public SeedResult seed(List<Map<String, Object>> competitions) {
        if (competitions == null || competitions.isEmpty()) {
            return new SeedResult("competition", 0, 0);
        }

        int inserted = 0;
        int skipped = 0;

        for (Map<String, Object> comp : competitions) {
            String code = (String) comp.get("code");
            String name = (String) comp.get("name");
            String slug = (String) comp.get("slug");

            if (slug == null || slug.isBlank()) {
                throw new IllegalArgumentException("Competition entry missing slug: " + comp);
            }

            UUID id = UUID.randomUUID();

            int res =
                    dsl.insertInto(
                                    T_COMPETITION,
                                    T_COMPETITION.PK_ID,
                                    T_COMPETITION.C_CODE,
                                    T_COMPETITION.C_NAME,
                                    T_COMPETITION.C_SLUG)
                            .values(id, code, name, slug)
                            .onConflict(T_COMPETITION.C_SLUG)
                            .doNothing()
                            .execute();

            if (res > 0) {
                inserted++;
            } else {
                skipped++;
            }
        }

        return new SeedResult("competition", inserted, skipped);
    }

}
