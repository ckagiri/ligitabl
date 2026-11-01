package com.ligitabl.seed.internal.schedule;

/** @param <T> the team identifier type */
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
