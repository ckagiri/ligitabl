package com.ligitabl.seed.internal.schedule;

import java.util.List;

/**
 * Represents a round in a sports schedule.
 * A round contains all matches played in that particular gameweek/round.
 *
 * @param position the week number (1-based)
 * @param matches the list of matches in this round
 * @param <T> the type of team identifier
 */
public record Round<T>(int position, List<Match<T>> matches) {

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Round %d (%d matches):%n", position, matches.size()));
        for (Match<T> match : matches) {
            sb.append(String.format("  %s%n", match));
        }
        return sb.toString();
    }
}
