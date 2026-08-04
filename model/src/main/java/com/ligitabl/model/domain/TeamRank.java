package com.ligitabl.model.domain;

import java.util.Comparator;
import java.util.List;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class TeamRank {
    String code;
    int position;

    public TeamRank(String code, int position) {
        this.code = code;
        this.position = position;
    }

    public static TeamRank of(String code, int position) {
        return new TeamRank(code, position);
    }

    public TeamRank withPosition(int newPosition) {
        return new TeamRank(this.code, newPosition);
    }

    /**
     * The list in the order a table is displayed: by position, ascending.
     *
     * <p>List order is <em>not</em> display order and never has been — a swap rewrites the two
     * teams' position values in place and leaves the list untouched (see
     * {@code SwapHelper.applySwap}). Reading a rankings list as if it were already ordered is
     * therefore wrong in a way that hides: it looks right until something swaps.
     */
    public static List<TeamRank> inPositionOrder(List<TeamRank> ranks) {
        return ranks == null
                ? List.of()
                : ranks.stream()
                        .sorted(Comparator.comparingInt(TeamRank::getPosition))
                        .toList();
    }

    @Override
    public String toString() {
        return String.format("#%d %s", position, code);
    }
}
