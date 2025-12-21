package com.ligitabl.seed.internal;

import static com.ligitabl.model.db.tables.TSeason.T_SEASON;
import static com.ligitabl.seed.internal.util.SeedCoercions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.seed.internal.util.SeedCoercions;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;

public class SeasonSeeder extends AbstractSeeder<List<Map<String, Object>>> {

    private final ObjectMapper objectMapper;
    private final ReferenceResolver referenceResolver;
    private final Map<String, Map<String, Object>> competitionsBySlug;

    public SeasonSeeder(DSLContext dsl, ObjectMapper objectMapper, List<Map<String, Object>> competitions) {
        super(dsl);
        this.objectMapper = objectMapper;
        this.referenceResolver = new ReferenceResolver(dsl);
        this.competitionsBySlug = indexCompetitionsBySlug(competitions);
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
        Integer clientId = asInteger(season.get("clientId"));
        String competitionSlug = asString(season.get("competitionSlug"));
        String name = asString(season.get("name"));
        String slug = asString(season.get("slug"));
        String startDate = asString(season.get("startDate"));
        String endDate = asString(season.get("endDate"));

        Integer maxRounds = firstInt(season, "max_rounds", "maxRounds");
        Integer maxHitPoints = firstInt(season, "max_hit_points", "maxHitPoints");
        Integer totalTeams = firstInt(season, "total_teams", "totalTeams");
        Boolean completed = firstBoolean(season, "completed");

        if (competitionSlug == null || competitionSlug.isBlank()) {
            throw new IllegalArgumentException("Season entry missing competitionSlug: " + season);
        }
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("Season entry missing slug: " + season);
        }

        Map<String, Object> competitionConfig = competitionsBySlug.get(competitionSlug);
        if (competitionConfig != null) {
            if (maxRounds == null) {
                maxRounds = firstInt(competitionConfig, "max_rounds", "maxRounds");
            }
            if (maxHitPoints == null) {
                maxHitPoints = firstInt(competitionConfig, "max_hit_points", "maxHitPoints");
            }
        }

        UUID competitionId = referenceResolver.resolveCompetitionId(competitionSlug);

        Object initialRankings = firstValue(season, "initial_rankings", "initialRankings", "teams");
        JSONB initialRankingsJson = serializeTeams(initialRankings, slug);

        if (totalTeams == null) {
            totalTeams = extractListSize(initialRankings);
        }

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
                T_SEASON.C_INITIAL_RANKINGS,
                T_SEASON.C_COMPLETED,
                T_SEASON.C_TOTAL_TEAMS,
                T_SEASON.C_MAX_HIT_POINTS,
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
                initialRankingsJson,
                completed != null ? completed : false,
                        totalTeams,
                maxHitPoints != null ? maxHitPoints : 0,
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

    private static Map<String, Map<String, Object>> indexCompetitionsBySlug(List<Map<String, Object>> competitions) {
        if (competitions == null || competitions.isEmpty()) {
            return Map.of();
        }

        return competitions.stream()
                .filter(c -> c.get("slug") instanceof String)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        c -> ((String) c.get("slug")),
                        c -> c,
                        (a, b) -> a));
    }

    private static Object firstValue(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key)) {
                Object value = map.get(key);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static Integer firstInt(Map<String, Object> map, String... keys) {
        Object value = firstValue(map, keys);
        return asInteger(value);
    }

    private static Boolean firstBoolean(Map<String, Object> map, String... keys) {
        Object value = firstValue(map, keys);
        return asBoolean(value);
    }

    private static int extractListSize(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }
        return 0;
    }
}


