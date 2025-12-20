package com.ligitabl.model.domain.standings.table;

import java.util.UUID;

public record TeamMatchView(UUID teamId, UUID opponentId, int goalsFor, int goalsAgainst, boolean wasHome) {
    public int points() {
        if (goalsFor > goalsAgainst) return 3;
        if (goalsFor == goalsAgainst) return 1;
        return 0;
    }

    public boolean won() {
        return goalsFor > goalsAgainst;
    }

    public boolean drew() {
        return goalsFor == goalsAgainst;
    }

    public boolean lost() {
        return goalsFor < goalsAgainst;
    }
}
