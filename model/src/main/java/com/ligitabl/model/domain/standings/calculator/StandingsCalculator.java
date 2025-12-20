package com.ligitabl.model.domain.standings.calculator;

import java.util.*;

import com.ligitabl.model.domain.standings.ranking.RankingRule;
import com.ligitabl.model.domain.standings.stats.Standing;
import com.ligitabl.model.domain.standings.stats.TeamStats;

/**
 * Calculator for league standings from team statistics.
 * This class is responsible solely for ranking teams and assigning positions,
 * not for storing or presenting the results.
 */
public final class StandingsCalculator {

    private StandingsCalculator() {
        throw new AssertionError("Cannot instantiate calculator class");
    }

    /**
     * Computes standings from team statistics using the provided ranking rule.
     * Teams are sorted according to the rule, and positions are assigned.
     * Teams with identical rankings share the same position.
     *
     * @param stats Collection of team statistics
     * @param rule The ranking rule to use for sorting
     * @return Immutable list of standings, sorted by position
     * @throws NullPointerException if stats or rule is null
     * @throws IllegalArgumentException if stats is empty
     */
    public static List<Standing> computeStandings(Collection<TeamStats> stats, RankingRule rule) {
        Objects.requireNonNull(stats, "Stats cannot be null");
        Objects.requireNonNull(rule, "Ranking rule cannot be null");

        if (stats.isEmpty()) {
            throw new IllegalArgumentException("At least one team's stats required");
        }

        // Sort teams according to the ranking rule
        List<TeamStats> sorted = stats.stream().sorted(rule::compare).toList();

        // Assign positions (teams with same rank share position)
        List<Standing> standings = new ArrayList<>();
        int currentPosition = 1;
        TeamStats previous = null;

        for (int i = 0; i < sorted.size(); i++) {
            TeamStats current = sorted.get(i);

            // If this team ranks differently from previous, update position
            if (previous != null && rule.compare(previous, current) != 0) {
                currentPosition = i + 1;
            }

            standings.add(new Standing(currentPosition, current));
            previous = current;
        }

        return List.copyOf(standings);
    }

    /**
     * Finds the position of a specific team in the standings.
     *
     * @param standings The standings list
     * @param teamId The team ID to find
     * @return Optional containing the Standing if found
     */
    public static Optional<Standing> findTeamStanding(List<Standing> standings, UUID teamId) {
        Objects.requireNonNull(standings, "Standings cannot be null");
        Objects.requireNonNull(teamId, "Team ID cannot be null");

        return standings.stream().filter(s -> s.stats().teamId().equals(teamId)).findFirst();
    }

    /**
     * Gets teams in a specific position range (e.g., top 4, bottom 3).
     *
     * @param standings The standings list
     * @param fromPosition Starting position (inclusive, 1-indexed)
     * @param toPosition Ending position (inclusive, 1-indexed)
     * @return List of standings in the range
     */
    public static List<Standing> getPositionRange(List<Standing> standings, int fromPosition, int toPosition) {
        Objects.requireNonNull(standings, "Standings cannot be null");

        if (fromPosition < 1) {
            throw new IllegalArgumentException("From position must be at least 1");
        }
        if (toPosition < fromPosition) {
            throw new IllegalArgumentException("To position must be >= from position");
        }

        return standings.stream()
                .filter(s -> s.position() >= fromPosition && s.position() <= toPosition)
                .toList();
    }
}
