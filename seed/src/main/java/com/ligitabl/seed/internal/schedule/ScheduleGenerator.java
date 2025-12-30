package com.ligitabl.seed.internal.schedule;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates soccer league schedules using the round-robin algorithm.
 *
 * Designed for leagues where each team plays every other team twice - once at
 * home and once away (double round-robin).
 *
 * @param <T> the type of team teamId (String, Integer, custom Team
 *            object, etc.)
 */
public class ScheduleGenerator<T> {

    /**
     * Generates a complete soccer league schedule (double round-robin).
     * Each team plays every other team twice - once at home and once away.
     *
     * @param teams the list of teams in the league (must be even number)
     * @return complete schedule with home and away matches
     * @throws IllegalArgumentException if teams list is null, has fewer than 2
     *                                  teams, or has an odd number of teams
     */
    public List<Round<T>> generateSchedule(List<T> teams) {
        validateTeams(teams);

        List<T> teamList = new ArrayList<>(teams);

        int totalTeams = teamList.size();
        int totalRounds = totalTeams - 1;

        List<Round<T>> schedule = generateRounds(teamList, totalRounds);

        List<Round<T>> returnFixtures = createReturnFixtures(schedule, totalRounds);
        schedule.addAll(returnFixtures);

        return schedule;
    }

    private List<Round<T>> generateRounds(List<T> teams, int totalRounds) {
        List<Round<T>> schedule = new ArrayList<>();

        List<T> positions = new ArrayList<>(teams);

        for (int roundPosition = 1; roundPosition <= totalRounds; roundPosition++) {
            List<Match<T>> matches = createMatchesForRound(positions);
            schedule.add(new Round<>(roundPosition, matches));

            if (roundPosition < totalRounds) {
                rotatePositions(positions);
            }
        }

        return schedule;
    }

    private List<Match<T>> createMatchesForRound(List<T> positions) {
        List<Match<T>> matches = new ArrayList<>();
        int matchesInWeek = positions.size() / 2;

        for (int i = 0; i < matchesInWeek; i++) {
            int leftIndex = i;
            int rightIndex = positions.size() - 1 - i;

            T leftTeam = positions.get(leftIndex);
            T rightTeam = positions.get(rightIndex);

            if (i % 2 == 0) {
                matches.add(new Match<>(leftTeam, rightTeam));
            } else {
                matches.add(new Match<>(rightTeam, leftTeam));
            }
        }

        return matches;
    }

    private void rotatePositions(List<T> positions) {
        if (positions.size() < 2) {
            return;
        }

        T rightJoker = positions.get(positions.size() - 1);

        for (int i = positions.size() - 1; i > 1; i--) {
            positions.set(i, positions.get(i - 1));
        }

        positions.set(1, rightJoker);
    }

    private List<Round<T>> createReturnFixtures(List<Round<T>> originalSchedule, int totalRounds) {
        List<Round<T>> returnSchedule = new ArrayList<>();

        for (int i = 0; i < originalSchedule.size(); i++) {
            Round<T> originalWeek = originalSchedule.get(i);
            List<Match<T>> returnMatches = new ArrayList<>();

            for (Match<T> match : originalWeek.matches()) {
                returnMatches.add(match.reverse());
            }

            int roundPosition = totalRounds + i + 1;
            returnSchedule.add(new Round<>(roundPosition, returnMatches));
        }

        return returnSchedule;
    }

    private void validateTeams(List<T> teams) {
        if (teams == null) {
            throw new IllegalArgumentException("Teams list cannot be null");
        }
        if (teams.size() < 2) {
            throw new IllegalArgumentException("Need at least 2 teams to generate a schedule");
        }
        if (teams.size() % 2 != 0) {
            throw new IllegalArgumentException(
                    "Soccer leagues must have an even number of teams. You provided "
                            + teams.size()
                            + " teams.");
        }
    }
}
