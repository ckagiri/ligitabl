package com.ligitabl.api.usecase.leaderboard.getleaderboard;

import java.util.UUID;

import com.ligitabl.model.domain.league.PhaseType;

/**
 * @param phase phase code chosen by the user; null means "use the preference, else the default"
 * @param preferredType the group the user last picked (from a cookie); ignored when {@code phase} is set
 */
public record GetLeaderboardQuery(String phase, Integer offset, Integer limit, UUID userId, PhaseType preferredType) {
    public GetLeaderboardQuery(String phase) {
        this(phase, null, null, null, null);
    }

    public GetLeaderboardQuery(String phase, Integer offset, Integer limit) {
        this(phase, offset, limit, null, null);
    }
}
