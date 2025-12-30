package com.ligitabl.seed.internal.schedule;

/**
 * Represents a single match between two teams.
 *
 * @param home the home team
 * @param away the away team
 * @param <T> the type of team identifier (String, Integer, custom Team object, etc.)
 */
public record Match<T>(T home, T away) {

    /**
     * Creates a reversed match (swapping home and away teams).
     *
     * @return a new Match with home and away teams swapped
     */
    public Match<T> reverse() {
        return new Match<>(away, home);
    }

    @Override
    public String toString() {
        return String.format("%s vs %s", home, away);
    }
}
