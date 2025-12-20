package com.ligitabl.model.domain.standings.stats;

import java.util.Objects;

public record Standing(int position, TeamStats stats) implements Comparable<Standing> {
    public Standing {
        Objects.requireNonNull(stats, "Team stats cannot be null");
        if (position < 1) throw new IllegalArgumentException("Position must be at least 1");
    }

    @Override
    public int compareTo(Standing other) {
        Objects.requireNonNull(other);
        return Integer.compare(this.position, other.position);
    }

    @Override
    public String toString() {
        return String.format("#%d: %s", position, stats);
    }
}
