package com.ligitabl.seed.internal;

import static com.ligitabl.model.db.tables.TSeason.T_SEASON;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;

public class SeasonSeeder extends AbstractSeeder<List<Map<String, Object>>> {

    private final ObjectMapper objectMapper;
    private final ReferenceResolver referenceResolver;

    public SeasonSeeder(DSLContext dsl, ObjectMapper objectMapper) {
        super(dsl);
        this.objectMapper = objectMapper;
        this.referenceResolver = new ReferenceResolver(dsl);
    }

    @Override
    protected boolean isValidConfig(List<Map<String, Object>> seasons) {
        return seasons != null && !seasons.isEmpty();
    }

    @Override
    protected void performSeed(List<Map<String, Object>> seasons) {
        for (Map<String, Object> season : seasons) {
            seedSeason(season);
        }
    }

    @Override
    protected String getSeederName() {
        return "season";
    }

    private void seedSeason(Map<String, Object> season) {
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

        UUID competitionId = referenceResolver.resolveCompetitionId(competitionSlug);
        JSONB teamsJson = serializeTeams(season.get("teams"), slug);

        if (seasonExists(competitionId, slug)) {
            recordSkip();
            return;
        }

        int rowsAffected = dsl.insertInto(
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
                .execute();

        if (rowsAffected > 0) {
            recordInsert();
        }
    }

    private boolean seasonExists(UUID competitionId, String slug) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(T_SEASON)
                        .where(T_SEASON.FK_COMPETITION_ID.eq(competitionId)
                                .and(T_SEASON.C_SLUG.eq(slug))));
    }

    private JSONB serializeTeams(Object teams, String slug) {
        if (teams == null) {
            return null;
        }

        try {
            String json = objectMapper.writeValueAsString(teams);
            return JSONB.valueOf(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Failed to serialise teams for season '" + slug + "'", e);
        }
    }
}

