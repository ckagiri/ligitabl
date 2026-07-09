package com.ligitabl.seed.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.seed.internal.config.RoundSeedConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jooq.DSLContext;


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

        DefaultsConfig configuredDefaults = parseDefaultsIfPresent(sections);
        requireDefaultsForProduction(configuredDefaults, mainResource);

        seedUsers(sections);
        seedTeams(sections);
        seedCompetitions(sections);
        seedSeasons(sections);
        seedRounds(sections);

        CurrentSeason currentSeason = resolveCurrentSeason(configuredDefaults);
        applyDefaults(currentSeason);
        seedMainContest(currentSeason);
        seedInitialStandings(currentSeason);

        return resultCollector.generateReport();
    }

    /**
     * Resolves the season whose date range covers "now" (or the soonest upcoming / most
     * recently ended one), so seeding config never needs editing when a new season starts.
     */
    private CurrentSeason resolveCurrentSeason(DefaultsConfig defaults) {
        if (defaults == null) {
            return null;
        }

        CurrentSeasonResolver resolver = new CurrentSeasonResolver(dsl);
        String resolvedSlug = resolver.resolveCurrentSeasonSlug(defaults.competitionSlug());
        return new CurrentSeason(defaults.competitionSlug(), resolvedSlug);
    }

    @SuppressWarnings("unchecked")
    private DefaultsConfig parseDefaultsIfPresent(Map<String, Object> sections) {
        Object defaultsObj = sections.get("defaults");
        if (!(defaultsObj instanceof Map<?, ?> defaultsMap)) {
            return null;
        }

        DefaultsConfig defaults = DefaultsConfig.fromMap((Map<String, Object>) defaultsMap);
        defaults.validateRequired();
        return defaults;
    }

    private void requireDefaultsForProduction(DefaultsConfig defaults, String mainResource) {
        if (!"seeding/main.yaml".equals(mainResource)) {
            return;
        }

        if (defaults == null) {
            throw new IllegalStateException(
                    "Missing required defaults section. Ensure seeding/defaults.yaml exists and is included by seeding/main.yaml");
        }
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
    private void seedTeams(Map<String, Object> sections) {
        List<Map<String, Object>> teams =
                (List<Map<String, Object>>) sections.getOrDefault("team", List.of());

        TeamSeeder seeder = new TeamSeeder(dsl);
        SeedResult result = seeder.seed(teams);
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
        if (!(roundConfig instanceof Map<?, ?> roundMap)) {
            return;
        }

        Map<String, Object> sharedDefaults = new HashMap<>();
        for (String key : List.of("namePrefix", "slugPrefix", "startPosition")) {
            if (roundMap.containsKey(key)) {
                sharedDefaults.put(key, roundMap.get(key));
            }
        }

        Object seasonsObj = roundMap.get("seasons");
        if (!(seasonsObj instanceof List<?> seasonEntries)) {
            return;
        }

        for (Object entryObj : seasonEntries) {
            if (!(entryObj instanceof Map<?, ?> entryMap)) {
                continue;
            }

            Map<String, Object> merged = new HashMap<>(sharedDefaults);
            merged.putAll((Map<String, Object>) entryMap);

            RoundSeedConfig config = RoundSeedConfig.fromMap(merged);
            RoundSeeder seeder = new RoundSeeder(dsl);
            SeedResult result = seeder.seed(config);
            resultCollector.add(result);
        }
    }

    private void applyDefaults(CurrentSeason currentSeason) {
        if (currentSeason == null) {
            return;
        }
        DefaultsSeeder defaultsSeeder = new DefaultsSeeder(dsl);
        DefaultsSeeder.DefaultsApplyResult result = defaultsSeeder.applyDefaults(currentSeason);

        resultCollector.add(asSetOrSkipped("active-season", result.activeSeasonSet()));
        resultCollector.add(asSetOrSkipped("current-round", result.currentRoundSet()));
    }

    private void seedMainContest(CurrentSeason currentSeason) {
        if (currentSeason == null) {
            return;
        }
        ContestSeeder seeder = new ContestSeeder(dsl);
        SeedResult result = seeder.seed(currentSeason);
        resultCollector.add(result);
        resultCollector.add(asSetOrSkipped("contest-fk", seeder.wasMainContestFkSet()));
    }

    /**
     * Reuses the inserted/skipped report shape for a single boolean fact: "set" (inserted=1)
     * vs. "already set, left alone" (skipped=1).
     */
    private static SeedResult asSetOrSkipped(String section, boolean set) {
        return set ? new SeedResult(section, 1, 0) : new SeedResult(section, 0, 1);
    }

    private void seedInitialStandings(CurrentSeason currentSeason) {
        if (currentSeason == null) {
            return;
        }
        StandingsSeeder seeder = new StandingsSeeder(dsl, objectMapper);
        SeedResult result = seeder.seed(currentSeason);
        resultCollector.add(result);
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

