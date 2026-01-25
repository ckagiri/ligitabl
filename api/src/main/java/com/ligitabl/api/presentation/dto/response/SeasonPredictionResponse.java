package com.ligitabl.api.presentation.dto.response;

import java.util.List;
import java.util.Objects;

/**
 * Response DTO for season prediction with rankings.
 *
 * <p>Includes a source indicator showing which tier of the fallback hierarchy was used:
 * - USER_PREDICTION: User's own prediction
 * - ROUND_STANDINGS: Current round standings
 * - SEASON_BASELINE: Season baseline rankings</p>
 */
public class SeasonPredictionResponse {

    private final String source;
    private final List<RankingDTO> rankings;
    private final String atRound;

    public SeasonPredictionResponse(
            String source,
            List<RankingDTO> rankings,
            String atRound
    ) {
        this.source = Objects.requireNonNull(source, "source is required");
        this.rankings = Objects.requireNonNull(rankings, "rankings are required");
        this.atRound = atRound; // Can be null for ROUND_STANDINGS/SEASON_BASELINE
    }

    public String getSource() {
        return source;
    }

    public List<RankingDTO> getRankings() {
        return rankings;
    }

    public String getAtRound() {
        return atRound;
    }

    /**
     * Check if this is the user's own prediction.
     *
     * @return true if source is USER_PREDICTION
     */
    public boolean isUserPrediction() {
        return "USER_PREDICTION".equals(source);
    }

    /**
     * Get a human-readable source label for display.
     *
     * @return source label
     */
    public String getSourceLabel() {
        return switch (source) {
            case "USER_PREDICTION" -> "Your Prediction";
            case "ROUND_STANDINGS" -> "Current Round Standings";
            case "SEASON_BASELINE" -> "Season Baseline";
            default -> "Unknown";
        };
    }

    @Override
    public String toString() {
        return "SeasonPredictionResponse{" +
                "source='" + source + '\'' +
                ", rankings=" + rankings.size() + " teams" +
                ", atRound='" + atRound + '\'' +
                '}';
    }
}
