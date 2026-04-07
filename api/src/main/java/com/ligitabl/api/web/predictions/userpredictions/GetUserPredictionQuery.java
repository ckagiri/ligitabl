package com.ligitabl.api.web.predictions.userpredictions;

import java.util.Objects;
import java.util.UUID;

import com.ligitabl.api.web.shared.user.UserContext;

/**
 * Command for retrieving user predictions with user context and optional round.
 */
public record GetUserPredictionQuery(UserContext userContext, UUID seasonId, Integer requestedRound) {
    public GetUserPredictionQuery {
        Objects.requireNonNull(userContext, "userContext is required");
        Objects.requireNonNull(seasonId, "seasonId is required");
        // requestedRound can be null (defaults to current round)
    }

    /**
     * Validate and normalize round number.
     * - If null or invalid → currentRound
     * - If > currentRound → currentRound
     * - If < 1 → currentRound
     */
    public int resolveRound(int currentRound, int maxRounds) {
        if (requestedRound == null) return currentRound;
        if (requestedRound < 1) return currentRound;
        if (requestedRound > currentRound) return currentRound;
        if (requestedRound > maxRounds) return currentRound;
        return requestedRound;
    }

    /**
     * Create command for an authenticated user viewing their own predictions.
     */
    public static GetUserPredictionQuery forAuthenticatedUser(
            UUID userId, UUID seasonId, boolean hasContestEntry, Integer round) {
        return new GetUserPredictionQuery(UserContext.authenticated(userId, hasContestEntry), seasonId, round);
    }

    /**
     * Create command for a guest user.
     */
    public static GetUserPredictionQuery forGuest(UUID seasonId, Integer round) {
        return new GetUserPredictionQuery(UserContext.guest(), seasonId, round);
    }

    /**
     * Create command for a non-existent user.
     */
    public static GetUserPredictionQuery forNonExistentUser(UUID seasonId, Integer round) {
        return new GetUserPredictionQuery(UserContext.userNotFound(), seasonId, round);
    }
}
