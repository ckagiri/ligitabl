package com.ligitabl.seed.internal;

import static com.ligitabl.model.db.tables.TSeason.T_SEASON;
import static com.ligitabl.seed.internal.util.SeedCoercions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.jooq.JSONB;

public class SeasonSeeder extends AbstractSeeder<List<Map<String, Object>>> {

    private static final Set<String> ALLOWED_RANKING_KEYS = Set.of("code", "position");

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
        // Enforce camelCase-only season config keys.
        for (String key : season.keySet()) {
            if (key != null && key.contains("_")) {
                throw new IllegalArgumentException(
                        "Season config uses snake_case key '" + key
                                + "'. Use camelCase keys only (e.g. totalTeams, maxRounds, maxHitPoints, initialRankings)."
                                + " Season entry: "
                                + season);
            }
        }

        Integer clientId = asInteger(season.get("clientId"));
        String competitionSlug = asString(season.get("competitionSlug"));
        String name = asString(season.get("name"));
        String slug = asString(season.get("slug"));
        String startDate = asString(season.get("startDate"));
        String endDate = asString(season.get("endDate"));

        Integer maxRounds = asInteger(season.get("maxRounds"));
        Integer maxHitPoints = asInteger(season.get("maxHitPoints"));
        Integer totalTeams = asInteger(season.get("totalTeams"));
        Boolean completed = asBoolean(season.get("completed"));

        if (competitionSlug == null || competitionSlug.isBlank()) {
            throw new IllegalArgumentException("Season entry missing competitionSlug: " + season);
        }
        if (slug == null || slug.isBlank()) {
            throw new IllegalArgumentException("Season entry missing slug: " + season);
        }

        if (totalTeams == null) {
            throw new IllegalArgumentException("Season entry missing totalTeams: " + season);
        }
        if (totalTeams <= 0) {
            throw new IllegalArgumentException(
                    "Season totalTeams must be > 0 (season slug: " + slug + ")");
        }

        Map<String, Object> competitionConfig = competitionsBySlug.get(competitionSlug);
        if (competitionConfig != null) {
            if (maxRounds == null) {
                maxRounds = asInteger(competitionConfig.get("maxRounds"));
            }
            if (maxHitPoints == null) {
                maxHitPoints = asInteger(competitionConfig.get("maxHitPoints"));
            }
        }

        UUID competitionId = referenceResolver.resolveCompetitionId(competitionSlug);
        Object initialRankings = season.get("initialRankings");

        if (initialRankings == null) {
            throw new IllegalArgumentException(
                    "Season entry missing initialRankings: " + season);
        }

        int rankingsCount = extractListSize(initialRankings);
        if (rankingsCount <= 0) {
            throw new IllegalArgumentException("Season initialRankings is empty for season slug: " + slug);
        }

        if (rankingsCount != totalTeams) {
            throw new IllegalArgumentException(
                "Season initialRankings length (" + rankingsCount + ") must equal totalTeams (" + totalTeams
                            + ") for season slug: " + slug);
        }

        // maxRounds derived: 2 × (totalTeams - 1)
        if (maxRounds == null) {
            maxRounds = 2 * (totalTeams - 1);
        }

        // maxHitPoints derived: 2 × (totalTeams/2)^2
        if (maxHitPoints == null) {
            int half = totalTeams / 2;
            maxHitPoints = 2 * half * half;
        }

        // Ensure all team codes exist (seeded teams are keyed by TLA).
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rankings = (List<Map<String, Object>>) initialRankings;

            for (Map<String, Object> ranking : rankings) {
                if (ranking == null) {
                    throw new IllegalArgumentException(
                            "Season initialRankings contains a null entry (season slug: " + slug + ")");
                }
                Set<String> keys = ranking.keySet();
                for (String k : keys) {
                    if (!ALLOWED_RANKING_KEYS.contains(k)) {
                        throw new IllegalArgumentException(
                                "Season initialRankings entries may only contain {code, position} (unexpected key '" + k
                                        + "', season slug: " + slug + ")");
                    }
                }
            }

            if (rankings.size() != totalTeams) {
            throw new IllegalArgumentException(
                "Season initialRankings must contain exactly totalTeams entries (season slug: " + slug + ")");
            }

            List<String> codes = rankings
                    .stream()
                    .map(r -> asString(r.get("code")))
                    .filter(c -> c != null && !c.isBlank())
                    .map(String::trim)
                    .collect(Collectors.toList());

            if (codes.size() != totalTeams) {
                throw new IllegalArgumentException(
                "Each initialRankings entry must have a non-blank 'code' (season slug: " + slug + ")");
            }

            Set<String> uniqueCodes = Set.copyOf(codes);
            if (uniqueCodes.size() != totalTeams) {
            throw new IllegalArgumentException(
                "Season initialRankings contains duplicate team codes (season slug: " + slug + ")");
            }

            List<Integer> positions = rankings.stream()
                .map(r -> asInteger(r.get("position")))
                .collect(Collectors.toList());

            if (positions.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                "Each initialRankings entry must have an integer 'position' (season slug: " + slug + ")");
            }

            Set<Integer> uniquePositions = Set.copyOf(positions);
            if (uniquePositions.size() != totalTeams) {
            throw new IllegalArgumentException(
                "Season initialRankings contains duplicate positions (season slug: " + slug + ")");
            }

            for (int i = 1; i <= totalTeams; i++) {
            if (!uniquePositions.contains(i)) {
                throw new IllegalArgumentException(
                    "Season initialRankings positions must cover 1..totalTeams with no gaps (missing " + i
                        + ", season slug: " + slug + ")");
            }
            }

            for (Integer position : uniquePositions) {
            if (position < 1 || position > totalTeams) {
                throw new IllegalArgumentException(
                    "Season initialRankings position out of range (" + position
                        + "). Expected 1..totalTeams (season slug: " + slug + ")");
            }
            }

            referenceResolver.resolveTeams(codes);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(
                "Season initialRankings must be a list of objects like {code, position} (season slug: " + slug
                            + ")",
                    e);
        }

        JSONB initialRankingsJson = serializeTeams(initialRankings, slug);

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
                    maxRounds,
                initialRankingsJson,
                completed != null ? completed : false,
                        totalTeams,
                maxHitPoints,
                        1)
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

    private static int extractListSize(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }
        return 0;
    }
}


