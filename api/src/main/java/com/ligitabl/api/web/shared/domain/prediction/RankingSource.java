package com.ligitabl.api.web.shared.domain.prediction;

/**
 * Enum representing the source of team rankings data.
 *
 * <p>This is used in the smart fallback hierarchy for GET /seasonprediction endpoint:</p>
 * <ol>
 *   <li>USER_PREDICTION - User's own season prediction (if exists)</li>
 *   <li>ROUND_STANDINGS - Current round standings (if available)</li>
 *   <li>SEASON_BASELINE - Default season baseline rankings (always exists)</li>
 * </ol>
 *
 * <p>The source is included in API responses to inform the client which data they're viewing.</p>
 *
 * <p>Example usage in response:</p>
 * <pre>
 * {
 *   "source": "ROUND_STANDINGS",
 *   "rankings": [...]
 * }
 * </pre>
 */
public enum RankingSource {
    /**
     * Rankings from the user's own season prediction.
     * This is the primary source when the user has already submitted predictions.
     */
    USER_PREDICTION("Your Prediction"),

    /**
     * Rankings from the current round's actual standings.
     * Used as a fallback for logged-in users who haven't submitted predictions yet.
     */
    ROUND_STANDINGS("Current Round Standings"),

    /**
     * Default baseline rankings for the season.
     * Used as the final fallback for all users (including guests).
     * This always exists as a game invariant.
     */
    SEASON_BASELINE("Season Baseline");

    private final String displayName;

    RankingSource(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Get a human-readable display name for this source.
     *
     * @return the display name suitable for UI web
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Check if this source represents user-created data.
     *
     * @return true if this is USER_PREDICTION, false otherwise
     */
    public boolean isUserPrediction() {
        return this == USER_PREDICTION;
    }

    /**
     * Check if this source represents a fallback (not user's own prediction).
     *
     * @return true if this is ROUND_STANDINGS or SEASON_BASELINE
     */
    public boolean isFallback() {
        return this != USER_PREDICTION;
    }
}
