package com.ligitabl.api.usecases.prediction.seeding;

import com.ligitabl.api.usecases.prediction.finalizeround.FinalizationResult;
import com.ligitabl.model.SwapChange;
import com.ligitabl.model.domain.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.IntStream;

public class SeedingTestFixtures {

    public static SeedingConfig createValidSeedingConfig() {
        SeedingConfig config = new SeedingConfig();
        config.setCompetitionSlug("premier-league");
        config.setSeasonSlug("2024-25");
        config.setFinishedRounds(18);

        List<SeedingConfig.DemoUser> users = List.of(
                createDemoUser("alice@demo.com", "Alice Wonder"),
                createDemoUser("bob@demo.com", "Bob Builder"),
                createDemoUser("charlie@demo.com", "Charlie Brown")
        );
        config.setDemoUsers(users);

        return config;
    }

    private static SeedingConfig.DemoUser createDemoUser(String email, String name) {
        SeedingConfig.DemoUser user = new SeedingConfig.DemoUser();
        user.setEmail(email);
        user.setDisplayName(name);
        return user;
    }

    public static Competition createCompetition() {
        return Competition.builder()
                .id(1L)
                .name("Premier League")
                .slug("premier-league")
                .country("England")
                .build();
    }

    public static Season createSeason(Competition competition) {
        List<TeamRank> initialRankings = IntStream.range(0, 12)
                .mapToObj(i -> new TeamRank(getTeamCode(i), i + 1))
                .toList();

        return Season.builder()
                .id(1L)
                .name("2024/25")
                .slug("2024-25")
                .competitionId(competition.getId())
                .competition(competition)
                .startDate(LocalDate.of(2024, 8, 1))
                .endDate(LocalDate.of(2025, 5, 31))
                .maxRounds(22)
                .totalTeams(12)
                .maxHitPoints(220)
                .initialRankings(initialRankings)
                .mainContestId(1L)
                .completed(false)
                .build();
    }

    public static List<Round> createRounds(Season season, int count) {
        return IntStream.range(1, count + 1)
                .mapToObj(i -> Round.builder()
                        .id((long) i)
                        .seasonId(season.getId())
                        .position(i)
                        .name("Round " + i)
                        .build())
                .toList();
    }

    public static List<Team> createTeams(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> Team.builder()
                        .id((long) (i + 1))
                        .name(getTeamName(i))
                        .code(getTeamCode(i))
                        .slug(getTeamSlug(i))
                        .logoUrl("https://example.com/logo-" + i + ".png")
                        .build())
                .toList();
    }

    public static Contest createContest(Season season) {
        return Contest.builder()
                .id(1L)
                .seasonId(season.getId())
                .name("Main League")
                .slug("main-league")
                .build();
    }

    public static List<Match> createMatches() {
        return List.of(
                Match.builder()
                        .id(1L)
                        .kickOff(Instant.now().plusSeconds(3600))
                        .build(),
                Match.builder()
                        .id(2L)
                        .kickOff(Instant.now().plusSeconds(7200))
                        .build()
        );
    }

    public static Standings createStandings() {
        List<StandingsTeamRank> rankings = IntStream.range(0, 12)
                .mapToObj(i -> StandingsTeamRank.builder()
                        .ranking(new TeamRank(getTeamCode(i), i + 1))
                        .metadata(new StandingsMetadata(10, 7, 2, 1, 23, 25, 10, 15))
                        .build())
                .toList();

        return Standings.builder()
                .id(1L)
                .seasonId(1L)
                .roundPosition(1)
                .rankings(rankings)
                .finalised(true)
                .finalisedAt(Instant.now())
                .build();
    }

    public static List<SwapChange> createSwapChanges() {
        return List.of(
                new SwapChange(Instant.now(), "MCI:1→2", "ARS:2→1"),
                new SwapChange(Instant.now().plusSeconds(86400), "LIV:3→4", "AVL:4→3")
        );
    }

    public static FinalizationResult createFinalizationResult() {
        return new FinalizationResult(
                1L,
                1,
                10,
                10,
                false,
                Instant.now()
        );
    }

    // Helper methods

    private static String getTeamName(int index) {
        String[] names = {"Manchester City", "Arsenal", "Liverpool", "Aston Villa",
                "Chelsea", "Newcastle", "Manchester United", "Tottenham",
                "Brighton", "Crystal Palace", "Brentford", "West Ham"};
        return names[index % names.length];
    }

    private static String getTeamCode(int index) {
        String[] codes = {"MCI", "ARS", "LIV", "AVL", "CHE", "NEW",
                "MUN", "TOT", "BHA", "CRY", "BRE", "WHU"};
        return codes[index % codes.length];
    }

    private static String getTeamSlug(int index) {
        return getTeamName(index).toLowerCase().replace(" ", "-");
    }
}
