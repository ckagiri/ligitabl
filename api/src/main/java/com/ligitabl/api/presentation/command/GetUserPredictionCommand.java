package com.ligitabl.api.presentation.command;

import com.ligitabl.api.presentation.domain.user.UserContext;

import java.util.Objects;
import java.util.UUID;

/**
 * Command for retrieving user predictions with user context and optional round.
 */
public record GetUserPredictionCommand(
        UserContext userContext,
        UUID seasonId,
        Integer requestedRound,
        String targetDisplayName
) {
    public GetUserPredictionCommand {
        Objects.requireNonNull(userContext, "userContext is required");
        Objects.requireNonNull(seasonId, "seasonId is required");
        // requestedRound can be null (defaults to current round)
        // targetDisplayName can be null (only set when viewing other user)
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
     * Check if the resolved round is historical (before current round).
     */
    public boolean isHistoricalRound(int currentRound, int maxRounds) {
        return resolveRound(currentRound, maxRounds) < currentRound;
    }

    /**
     * Create command for an authenticated user viewing their own predictions.
     */
    public static GetUserPredictionCommand forAuthenticatedUser(
            UUID userId,
            UUID seasonId,
            boolean hasMainContestEntry,
            Integer round
    ) {
        return new GetUserPredictionCommand(
                UserContext.authenticated(userId, hasMainContestEntry),
                seasonId,
                round,
                null
        );
    }

    /**
     * Create command for a guest user.
     */
    public static GetUserPredictionCommand forGuest(UUID seasonId, Integer round) {
        return new GetUserPredictionCommand(
                UserContext.guest(),
                seasonId,
                round,
                null
        );
    }

    /**
     * Create command for viewing another user's predictions.
     */
    public static GetUserPredictionCommand forViewingOtherUser(
            UUID targetUserId,
            UUID seasonId,
            boolean hasMainContestEntry,
            String displayName,
            Integer round
    ) {
        return new GetUserPredictionCommand(
                UserContext.viewingOther(targetUserId, hasMainContestEntry),
                seasonId,
                round,
                displayName
        );
    }

    /**
     * Create command for a non-existent user.
     */
    public static GetUserPredictionCommand forNonExistentUser(UUID seasonId, Integer round) {
        return new GetUserPredictionCommand(
                UserContext.userNotFound(),
                seasonId,
                round,
                null
        );
    }
}
