package com.ligitabl.model.domain;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class TeamRank {
    String code;
    int position;

    public static TeamRank of(String code, int position) {
        return new TeamRank(code, position);
    }

    public TeamRank withPosition(int newPosition) {
        return new TeamRank(this.code, newPosition);
    }

    @Override
    public String toString() {
        return String.format("#%d %s", position, code);
    }
}
