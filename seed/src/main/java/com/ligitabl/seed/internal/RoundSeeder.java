package com.ligitabl.seed.internal;

import static com.ligitabl.model.db.tables.TCompetition.T_COMPETITION;
import static com.ligitabl.model.db.tables.TRound.T_ROUND;
import static com.ligitabl.model.db.tables.TSeason.T_SEASON;

import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;

public class RoundSeeder {

    private final DSLContext dsl;

    public RoundSeeder(DSLContext dsl) {
        this.dsl = dsl;
    }

    @SuppressWarnings("unchecked")
    public SeedResult seed(Object roundConfig) {
        if (!(roundConfig instanceof Map<?, ?> map)) {
            return new SeedResult("round", 0, 0);
        }

        Map<String, Object> cfg = (Map<String, Object>) map;

        String competitionSlug = (String) cfg.get("competitionSlug");
        String seasonSlug = (String) cfg.get("seasonSlug");
        Integer count = (Integer) cfg.get("count");
        String namePrefix = (String) cfg.getOrDefault("namePrefix", "Round ");
        String slugPrefix = (String) cfg.getOrDefault("slugPrefix", "round-");
        Integer startPosition = (Integer) cfg.getOrDefault("startPosition", 1);

        if (competitionSlug == null || competitionSlug.isBlank()) {
            throw new IllegalArgumentException("Round config missing competitionSlug: " + cfg);
        }
        if (seasonSlug == null || seasonSlug.isBlank()) {
            throw new IllegalArgumentException("Round config missing seasonSlug: " + cfg);
        }

        if (count == null || count <= 0) {
            return new SeedResult("round", 0, 0);
        }

        UUID competitionId =
            dsl.select(T_COMPETITION.PK_ID)
                .from(T_COMPETITION)
                .where(T_COMPETITION.C_SLUG.eq(competitionSlug))
                .fetchOne(T_COMPETITION.PK_ID);

        if (competitionId == null) {
            throw new IllegalStateException(
                "No competition found for slug '" + competitionSlug + "' while seeding rounds");
        }

        UUID seasonId =
            dsl.select(T_SEASON.PK_ID)
                .from(T_SEASON)
                .where(T_SEASON.FK_COMPETITION_ID.eq(competitionId)
                    .and(T_SEASON.C_SLUG.eq(seasonSlug)))
                .fetchOne(T_SEASON.PK_ID);

        if (seasonId == null) {
            throw new IllegalStateException(
                "No season found for competitionSlug='" + competitionSlug + "', seasonSlug='" + seasonSlug
                    + "' while seeding rounds");
        }

        int inserted = 0;
        int skipped = 0;

        for (int i = 0; i < count; i++) {
            int position = startPosition + i;
            String name = namePrefix + position;
            String slug = slugPrefix + position;

            int res =
                    dsl.insertInto(
                                    T_ROUND,
                                    T_ROUND.PK_ID,
                                    T_ROUND.FK_SEASON_ID,
                                    T_ROUND.C_NAME,
                                    T_ROUND.C_SLUG,
                                    T_ROUND.C_POSITION)
                            .values(UUID.randomUUID(), seasonId, name, slug, position)
                            .onConflict(T_ROUND.FK_SEASON_ID, T_ROUND.C_POSITION)
                            .doNothing()
                            .execute();

            if (res > 0) {
                inserted++;
            } else {
                skipped++;
            }
        }

        return new SeedResult("round", inserted, skipped);
    }
}
