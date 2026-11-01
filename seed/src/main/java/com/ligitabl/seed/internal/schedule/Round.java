package com.ligitabl.seed.internal.schedule;

import java.util.List;

/**
 * @param position the gameweek, 1-based
 * @param <T> the team identifier type
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
