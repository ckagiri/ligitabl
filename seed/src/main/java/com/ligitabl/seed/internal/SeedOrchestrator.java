package com.ligitabl.seed.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.seed.internal.config.MatchSeedConfig;
import com.ligitabl.seed.internal.config.RoundSeedConfig;
import com.ligitabl.seed.internal.util.SeedCoercions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;

import static com.ligitabl.seed.internal.util.SeedCoercions.asInteger;
import static com.ligitabl.seed.internal.util.SeedCoercions.asString;

/**
 * Orchestrates the seeding process across multiple seeders.
 * Handles configuration parsing and execution order.
 */
public class SeedOrchestrator {

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;
    private final SeedResultCollector resultCollector;

    public SeedOrchestrator(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
        this.resultCollector = new SeedResultCollector();
    }

    public SeedExecutionReport executeSeed(Map<String, Object> sections, String mainResource) {
        resultCollector.clear();

        seedUsers(sections);
        seedTeams(sections);
        seedCompetitions(sections);
        seedSeasons(sections);
        seedRounds(sections);
        seedMatches(sections, mainResource);
        applyDefaults();

        return resultCollector.generateReport();
    }

    @SuppressWarnings("unchecked")
    private void seedTeams(Map<String, Object> sections) {
        List<Map<String, Object>> teams =
                (List<Map<String, Object>>) sections.getOrDefault("team", List.of());

        TeamSeeder seeder = new TeamSeeder(dsl);
        SeedResult result = seeder.seed(teams);
        resultCollector.add(result);
    }

    @SuppressWarnings("unchecked")
    private void seedUsers(Map<String, Object> sections) {
        List<Map<String, Object>> users =
                (List<Map<String, Object>>) sections.getOrDefault("user", List.of());

        UserSeeder seeder = new UserSeeder(dsl);
        SeedResult result = seeder.seed(users);
        resultCollector.add(result);
    }

    @SuppressWarnings("unchecked")
    private void seedCompetitions(Map<String, Object> sections) {
        List<Map<String, Object>> competitions =
                (List<Map<String, Object>>) sections.getOrDefault("competition", List.of());

        CompetitionSeeder seeder = new CompetitionSeeder(dsl, objectMapper);
        SeedResult result = seeder.seed(competitions);
        resultCollector.add(result);
    }

    @SuppressWarnings("unchecked")
    private void seedSeasons(Map<String, Object> sections) {
        List<Map<String, Object>> seasons =
                (List<Map<String, Object>>) sections.getOrDefault("season", List.of());

        List<Map<String, Object>> competitions =
            (List<Map<String, Object>>) sections.getOrDefault("competition", List.of());

        SeasonSeeder seeder = new SeasonSeeder(dsl, objectMapper, competitions);
        SeedResult result = seeder.seed(seasons);
        resultCollector.add(result);
    }

    @SuppressWarnings("unchecked")
    private void seedRounds(Map<String, Object> sections) {
        Object roundConfig = sections.get("round");
        if (roundConfig instanceof Map<?, ?> map) {
            RoundSeedConfig config = RoundSeedConfig.fromMap((Map<String, Object>) map);
            RoundSeeder seeder = new RoundSeeder(dsl);
            SeedResult result = seeder.seed(config);
            resultCollector.add(result);
        }
    }

    @SuppressWarnings("unchecked")
    private void seedMatches(Map<String, Object> sections, String mainResource) {
        Object matchConfig = null;

        if (isDemoResource(mainResource)) {
            matchConfig = createAutoMatchConfig(sections);
        }

        if (matchConfig instanceof Map<?, ?> map) {
            MatchSeedConfig config = MatchSeedConfig.fromMap((Map<String, Object>) map);
            MatchSeeder seeder = new MatchSeeder(dsl, objectMapper);
            SeedResult result = seeder.seed(config);
            resultCollector.add(result);
        }
    }

    private void applyDefaults() {
        DefaultsSeeder defaultsSeeder = new DefaultsSeeder(dsl);
        defaultsSeeder.applyDefaults();
    }

    private boolean isDemoResource(String mainResource) {
        return "seeding/demo-main.yaml".equals(mainResource);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createAutoMatchConfig(Map<String, Object> sections) {
        List<Map<String, Object>> seasons =
                (List<Map<String, Object>>) sections.getOrDefault("season", List.of());

        if (seasons.isEmpty()) {
            return null;
        }

        Map<String, Object> firstSeason = seasons.get(0);
        String competitionSlug = asString(firstSeason.get("competitionSlug"));
        String seasonSlug = asString(firstSeason.get("slug"));
        Integer finishedRounds = asInteger(firstSeason.get("finishedRounds"));

        if (competitionSlug == null || seasonSlug == null) {
            return null;
        }

        return Map.of(
                "competitionSlug", competitionSlug,
                "seasonSlug", seasonSlug,
                "clientId", 1,
                "scheduledStatus", "SCHEDULED",
                "finishedStatus", "FINISHED",
                "finishedRounds", finishedRounds != null ? finishedRounds : 0);
    }

    private static class SeedResultCollector {
        private final List<SeedResult> results = new ArrayList<>();

        void clear() {
            results.clear();
        }

        void add(SeedResult result) {
            results.add(result);
        }

        SeedExecutionReport generateReport() {
            return new SeedExecutionReport(results);
        }
    }

    public static class SeedExecutionReport {
        private final List<SeedResult> results;

        public SeedExecutionReport(List<SeedResult> results) {
            this.results = List.copyOf(results);
        }

        public List<SeedResult> getResults() {
            return results;
        }

        public void printToConsole() {
            for (SeedResult result : results) {
                System.out.printf(
                        "[seed] %-12s inserted=%d skipped=%d%n",
                        result.section(),
                        result.inserted(),
                        result.skipped());
            }
        }
    }
}

