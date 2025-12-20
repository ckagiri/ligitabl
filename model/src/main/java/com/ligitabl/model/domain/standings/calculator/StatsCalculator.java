package com.ligitabl.model.domain.standings.calculator;

import java.util.*;

import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.standings.stats.TeamStats;

/**
 * Calculator for team statistics from match results.
 * Separated from LeagueTable for better separation of concerns.
 *
 * This class is responsible solely for computing statistics,
 * not for storing or presenting them.
 */
public final class StatsCalculator {

    private StatsCalculator() {
        throw new AssertionError("Cannot instantiate calculator class");
    }

    /**
     * Computes statistics for a list of teams based on their matches.
     * Only played matches are considered.
     *
     * @param teamIds List of team IDs to compute stats for
     * @param matches List of all matches (will be filtered for relevant teams)
     * @return Map of team ID to their statistics
     * @throws NullPointerException if teamIds or matches is null
     * @throws IllegalArgumentException if teamIds is empty
     */
    public static Map<UUID, TeamStats> computeStats(List<UUID> teamIds, List<Match> matches) {
        Objects.requireNonNull(teamIds, "Team IDs cannot be null");
        Objects.requireNonNull(matches, "Matches cannot be null");

        if (teamIds.isEmpty()) {
            throw new IllegalArgumentException("At least one team ID required");
        }

        // Initialize empty stats for each team
        Map<UUID, TeamStats> stats = new HashMap<>();
        teamIds.forEach(id -> {
            if (id == null) {
                throw new IllegalArgumentException("Team ID cannot be null or blank");
            }
            stats.put(id, TeamStats.empty(id));
        });

        // Process each played match
        for (Match match : matches) {
            if (match == null) {
                continue; // Skip null matches
            }

            if (!match.isPlayed()) {
                continue; // Skip unplayed matches
            }

            // Update home team stats
            match.viewFor(match.getHomeTeamId())
                    .ifPresent(view -> stats.computeIfPresent(match.getHomeTeamId(), (id, s) -> s.withMatch(view)));

            // Update away team stats
            match.viewFor(match.getAwayTeamId())
                    .ifPresent(view -> stats.computeIfPresent(match.getAwayTeamId(), (id, s) -> s.withMatch(view)));
        }

        return stats;
    }

    /**
     * Computes statistics for a single team.
     *
     * @param teamId The team ID
     * @param matches All matches
     * @return Statistics for the team
     */
    public static TeamStats computeStatsForTeam(UUID teamId, List<Match> matches) {
        Objects.requireNonNull(teamId, "Team ID cannot be null");
        Objects.requireNonNull(matches, "Matches cannot be null");

        return computeStats(List.of(teamId), matches).get(teamId);
    }
}
