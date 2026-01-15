package com.ligitabl.api.controllers.web;

import java.time.Instant;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * In-memory fake data service for UI development and testing.
 * Enable with: ligitabl.ui.fake-data.enabled=true
 *
 * This allows you to see the UI working without database dependencies.
 */
@Service
@ConditionalOnProperty(name = "ligitabl.ui.fake-data.enabled", havingValue = "true", matchIfMissing = false)
public class FakeWebDataService {

    public FakeLeaderboard getFakeLeaderboard(String phase) {
        var users = List.of(
                new FakeLeaderboardEntry(1, "Alice Wonder", 285, 285, 0, 0, 0),
                new FakeLeaderboardEntry(2, "Bob Builder", 278, 278, 7, 1, 0),
                new FakeLeaderboardEntry(3, "Charlie Brown", 265, 265, 20, 2, 1),
                new FakeLeaderboardEntry(4, "Diana Prince", 258, 258, 27, 3, -1),
                new FakeLeaderboardEntry(5, "Eve Adams", 252, 252, 33, 4, 2),
                new FakeLeaderboardEntry(6, "Frank Castle", 245, 245, 40, 5, 0),
                new FakeLeaderboardEntry(7, "Grace Hopper", 238, 238, 47, 6, 3),
                new FakeLeaderboardEntry(8, "Henry Ford", 231, 231, 54, 7, -2),
                new FakeLeaderboardEntry(9, "Iris West", 224, 224, 61, 8, 1),
                new FakeLeaderboardEntry(10, "Jack Sparrow", 217, 217, 68, 9, -1));

        return new FakeLeaderboard(phase, users, 10, 1, 19);
    }

    public FakeMatches getFakeMatches() {
        var matches = List.of(
                new FakeMatch("ARS", "Arsenal", "LIV", "Liverpool", "2024-12-15T15:00:00Z", 2, 1, "FINISHED"),
                new FakeMatch("MCI", "Man City", "CHE", "Chelsea", "2024-12-15T15:00:00Z", 3, 1, "FINISHED"),
                new FakeMatch("MUN", "Man United", "TOT", "Tottenham", "2024-12-15T17:30:00Z", 1, 1, "FINISHED"),
                new FakeMatch("NEW", "Newcastle", "AVL", "Aston Villa", "2024-12-16T14:00:00Z", 0, 2, "FINISHED"),
                new FakeMatch("WHU", "West Ham", "BHA", "Brighton", "2024-12-16T14:00:00Z", 1, 3, "FINISHED"),
                new FakeMatch("WOL", "Wolves", "FUL", "Fulham", "2024-12-16T16:30:00Z", 2, 2, "FINISHED"),
                new FakeMatch(
                        "BOU", "Bournemouth", "CRY", "Crystal Palace", "2024-12-17T20:00:00Z", null, null, "SCHEDULED"),
                new FakeMatch("BRE", "Brentford", "EVE", "Everton", "2024-12-18T19:45:00Z", null, null, "SCHEDULED"),
                new FakeMatch(
                        "NFO", "Nottingham Forest", "BUR", "Burnley", "2024-12-19T20:00:00Z", null, null, "SCHEDULED"),
                new FakeMatch(
                        "LEE", "Leeds United", "SUN", "Sunderland", "2024-12-20T15:00:00Z", null, null, "SCHEDULED"));

        return new FakeMatches("premier-league", "2024-25", 19, matches);
    }

    public FakePrediction getFakePrediction() {
        var teams = List.of(
                new FakeTeamRank(1, "MCI", "Manchester City", "/images/crests/mci.png", 0),
                new FakeTeamRank(2, "ARS", "Arsenal", "/images/crests/ars.png", 0),
                new FakeTeamRank(3, "LIV", "Liverpool", "/images/crests/liv.png", 1),
                new FakeTeamRank(4, "AVL", "Aston Villa", "/images/crests/avl.png", 1),
                new FakeTeamRank(5, "TOT", "Tottenham", "/images/crests/tot.png", 0),
                new FakeTeamRank(6, "CHE", "Chelsea", "/images/crests/che.png", 1),
                new FakeTeamRank(7, "NEW", "Newcastle", "/images/crests/new.png", 3),
                new FakeTeamRank(8, "MUN", "Man United", "/images/crests/mun.png", 2),
                new FakeTeamRank(9, "WHU", "West Ham", "/images/crests/whu.png", 1),
                new FakeTeamRank(10, "BHA", "Brighton", "/images/crests/bha.png", 0),
                new FakeTeamRank(11, "WOL", "Wolves", "/images/crests/wol.png", 2),
                new FakeTeamRank(12, "FUL", "Fulham", "/images/crests/ful.png", 1),
                new FakeTeamRank(13, "BOU", "Bournemouth", "/images/crests/bou.png", 4),
                new FakeTeamRank(14, "CRY", "Crystal Palace", "/images/crests/cry.png", 1),
                new FakeTeamRank(15, "BRE", "Brentford", "/images/crests/bre.png", 0),
                new FakeTeamRank(16, "EVE", "Everton", "/images/crests/eve.png", 2),
                new FakeTeamRank(17, "NFO", "Nottingham Forest", "/images/crests/nfo.png", 3),
                new FakeTeamRank(18, "LEE", "Leeds United", "/images/crests/lee.png", 1),
                new FakeTeamRank(19, "BUR", "Burnley", "/images/crests/bur.png", 2),
                new FakeTeamRank(20, "SUN", "Sunderland", "/images/crests/sun.png", 5));

        return new FakePrediction(teams, 285, 19, true, Instant.now().minus(12, java.time.temporal.ChronoUnit.HOURS));
    }

    // Fake data records
    public record FakeLeaderboardEntry(
            int position,
            String displayName,
            int totalScore,
            int maxScore,
            int pointsBehind,
            int zeroes,
            int movement) {}

    public record FakeLeaderboard(
            String phase, List<FakeLeaderboardEntry> entries, int totalUsers, int pageNumber, int currentRound) {}

    public record FakeMatch(
            String homeCode,
            String homeName,
            String awayCode,
            String awayName,
            String kickOffTime,
            Integer homeScore,
            Integer awayScore,
            String status) {}

    public record FakeMatches(String competition, String season, int round, List<FakeMatch> matches) {}

    public record FakeTeamRank(int position, String code, String name, String crestUrl, int pointsOff) {}

    public record FakePrediction(
            List<FakeTeamRank> teams, int totalPoints, int currentRound, boolean canSwap, Instant nextSwapAt) {}
}
