package com.ligitabl.api.rest.leaderboard.getleaderboard;

import java.util.UUID;

public record GetLeaderboardQuery(String phase, Integer offset, Integer limit, UUID userId) {
    public GetLeaderboardQuery(String phase) {
        this(phase, null, null, null);
    }

    public GetLeaderboardQuery(String phase, Integer offset, Integer limit) {
        this(phase, offset, limit, null);
    }
}
