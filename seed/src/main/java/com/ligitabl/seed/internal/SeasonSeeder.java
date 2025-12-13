package com.ligitabl.seed.internal;

import static com.ligitabl.model.db.tables.TCompetition.T_COMPETITION;
import static com.ligitabl.model.db.tables.TSeason.T_SEASON;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;

public class SeasonSeeder {

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public SeasonSeeder(DSLContext dsl) {
        this.dsl = dsl;
        this.objectMapper = new ObjectMapper();
    }

    public SeedResult seed(List<Map<String, Object>> seasons) {
        if (seasons == null || seasons.isEmpty()) {
            return new SeedResult("season", 0, 0);
        }

        int inserted = 0;
        int skipped = 0;

        for (Map<String, Object> season : seasons) {
            Integer clientId = (Integer) season.get("clientId");
            String competitionSlug = (String) season.get("competitionSlug");
            String name = (String) season.get("name");
            String slug = (String) season.get("slug");
            String startDate = (String) season.get("startDate");
            String endDate = (String) season.get("endDate");
            Integer maxRounds = (Integer) season.get("maxRounds");

            if (competitionSlug == null || competitionSlug.isBlank()) {
                throw new IllegalArgumentException("Season entry missing competitionSlug: " + season);
            }
            if (slug == null || slug.isBlank()) {
                throw new IllegalArgumentException("Season entry missing slug: " + season);
            }

                UUID competitionId =
                    dsl.select(T_COMPETITION.PK_ID)
                        .from(T_COMPETITION)
                        .where(T_COMPETITION.C_SLUG.eq(competitionSlug))
                        .fetchOne(T_COMPETITION.PK_ID);

                if (competitionId == null) {
                throw new IllegalStateException(
                    "No competition found for slug '" + competitionSlug + "' while seeding season " + slug);
                }

            JSONB teamsJson = null;
            Object teams = season.get("teams");
            if (teams != null) {
                try {
                    String json = objectMapper.writeValueAsString(teams);
                    teamsJson = JSONB.valueOf(json);
                } catch (JsonProcessingException e) {
                    throw new IllegalArgumentException("Failed to serialise teams for season " + slug, e);
                }
            }

            int res =
                    dsl.insertInto(
                                    T_SEASON,
                                    T_SEASON.PK_ID,
                                    T_SEASON.C_CLIENT_ID,
                                    T_SEASON.FK_COMPETITION_ID,
                                    T_SEASON.C_NAME,
                                    T_SEASON.C_SLUG,
                                    T_SEASON.C_START_DATE,
                                    T_SEASON.C_END_DATE,
                                    T_SEASON.C_MAX_ROUNDS,
                                    T_SEASON.C_TEAMS,
                                    T_SEASON.C_CURRENT_MATCH_DAY)
                            .values(
                                    UUID.randomUUID(),
                                    clientId != null ? clientId : 1,
                                    competitionId,
                                    name,
                                    slug,
                                    LocalDate.parse(startDate),
                                    LocalDate.parse(endDate),
                                    maxRounds != null ? maxRounds : 0,
                                    teamsJson,
                                    0)
                            .onConflict(T_SEASON.C_SLUG)
                            .doNothing()
                            .execute();

            if (res > 0) {
                inserted++;
            } else {
                skipped++;
            }
        }

        return new SeedResult("season", inserted, skipped);
    }
}
