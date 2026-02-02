package com.ligitabl.api.rest.demoseeding;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.IntStream;

import com.ligitabl.api.runners.demoseeding.SeedingConfig;
import com.ligitabl.api.rest.round.finalizeround.FinalizeRoundResult;
import com.ligitabl.model.domain.*;

public class SeedingTestFixtures {

    public static SeedingConfig createValidSeedingConfig() {
        SeedingConfig config = new SeedingConfig();
        config.setCompetitionSlug("premier-league");
        config.setSeasonSlug("2024-25");
        config.setFinishedRounds(18);

        List<SeedingConfig.DemoUser> users = List.of(
                createDemoUser("alice@demo.com", "Alice Wonder"),
                createDemoUser("bob@demo.com", "Bob Builder"),
                createDemoUser("charlie@demo.com", "Charlie Brown"));
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
                .id(UUID.randomUUID())
                .name("Premier League")
                .slug(CompetitionSlug.of("premier-league"))
                .code("EPL")
                .build();
    }

    public static Season createSeason(Competition competition) {
        List<TeamRank> initialRankings = IntStream.range(0, 12)
                .mapToObj(i -> new TeamRank(getTeamCode(i), i + 1))
                .toList();

        return Season.builder()
                .id(UUID.randomUUID())
                .clientId(1)
                .name("2024/25")
                .slug(SeasonSlug.of("2024-25"))
                .competitionId(competition.getId())
                .startDate(LocalDate.of(2024, 8, 1))
                .endDate(LocalDate.of(2025, 5, 31))
                .maxRounds(22)
                .totalTeams(12)
                .maxHitPoints(220)
                .initialRankings(initialRankings)
                .completed(false)
                .build();
    }

    public static List<Round> createRounds(Season season, int count) {
        return IntStream.range(1, count + 1)
                .mapToObj(i -> (Round) Round.builder()
                        .id(UUID.randomUUID())
                        .seasonId(season.getId())
                        .position(i)
                        .name("Round " + i)
                        .slug("round-" + i)
                        .build())
                .toList();
    }

    public static List<Team> createTeams(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> (Team) Team.builder()
                        .id(UUID.randomUUID())
                        .clientId(i + 1)
                        .name(getTeamName(i))
                        .shortName(getTeamCode(i))
                        .tla(getTeamCode(i))
                        .slug(TeamSlug.of(getTeamSlug(i)))
                        .build())
                .toList();
    }

    public static Contest createContest(Season season) {
        return Contest.builder()
                .id(UUID.randomUUID())
                .seasonId(season.getId())
                .name("Main League")
                .isPrivate(false)
                .fromRoundPosition(1)
                .build();
    }

    public static List<Match> createMatches(UUID seasonId, UUID roundId) {
        UUID homeTeamId = UUID.randomUUID();
        UUID awayTeamId = UUID.randomUUID();

        return List.of(
                Match.builder()
                        .id(UUID.randomUUID())
                        .clientId(101)
                        .homeTeamId(homeTeamId)
                        .awayTeamId(awayTeamId)
                        .seasonId(seasonId)
                        .roundId(roundId)
                        .slug("home-vs-away")
                        .status(MatchStatus.SCHEDULED)
                        .kickOff(OffsetDateTime.now().plusHours(1))
                        .build(),
                Match.builder()
                        .id(UUID.randomUUID())
                        .clientId(102)
                        .homeTeamId(homeTeamId)
                        .awayTeamId(awayTeamId)
                        .seasonId(seasonId)
                        .roundId(roundId)
                        .slug("home-vs-away-2")
                        .status(MatchStatus.SCHEDULED)
                        .kickOff(OffsetDateTime.now().plusHours(2))
                        .build());
    }

    public static Standings createStandings(UUID seasonId) {
        List<StandingsTeamRank> rankings = IntStream.range(0, 12)
                .mapToObj(i -> StandingsTeamRank.builder()
                        .ranking(new TeamRank(getTeamCode(i), i + 1))
                        .metadata(new StandingsMetadata(10, 7, 2, 1, 23, 25, 10, 15))
                        .build())
                .toList();

        return Standings.builder()
                .id(UUID.randomUUID())
                .seasonId(seasonId)
                .roundPosition(1)
                .rankings(rankings)
                .finalised(true)
                .finalisedAt(OffsetDateTime.now())
                .build();
    }

    public static FinalizeRoundResult createFinalizationResult() {
        return new FinalizeRoundResult(UUID.randomUUID(), 1, 10, 10, false, Instant.now());
    }

    // Helper methods

    private static String getTeamName(int index) {
        String[] names = {
            "Manchester City",
            "Arsenal",
            "Liverpool",
            "Aston Villa",
            "Chelsea",
            "Newcastle",
            "Manchester United",
            "Tottenham",
            "Brighton",
            "Crystal Palace",
            "Brentford",
            "West Ham"
        };
        return names[index % names.length];
    }

    private static String getTeamCode(int index) {
        String[] codes = {"MCI", "ARS", "LIV", "AVL", "CHE", "NEW", "MUN", "TOT", "BHA", "CRY", "BRE", "WHU"};
        return codes[index % codes.length];
    }

    private static String getTeamSlug(int index) {
        return getTeamName(index).toLowerCase().replace(" ", "-");
    }
}
