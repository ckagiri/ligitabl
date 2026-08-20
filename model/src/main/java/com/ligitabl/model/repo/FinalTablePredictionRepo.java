package com.ligitabl.model.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.FinalTableEntrant;
import com.ligitabl.model.domain.FinalTableLeaderboardEntry;
import com.ligitabl.model.domain.FinalTablePrediction;

public interface FinalTablePredictionRepo {
    Optional<FinalTablePrediction> findByUserAndSeason(UUID userId, UUID seasonId);

    List<FinalTablePrediction> findBySeason(UUID seasonId);

    FinalTablePrediction save(FinalTablePrediction prediction);

    /** Scored rows only, ranked by score, then zeroes, then who settled first. */
    List<FinalTableLeaderboardEntry> leaderboard(UUID seasonId, int offset, int limit);

    /** This user's row in the same ranking, or empty if they have none or it is unscored. */
    Optional<FinalTableLeaderboardEntry> userStanding(UUID seasonId, UUID userId);

    /**
     * Everyone with an entry this season, scored or not, in the order they settled.
     */
    List<FinalTableEntrant> entrantsBySeason(UUID seasonId);

    int countBySeason(UUID seasonId);

    /** Drives the leaderboard's reveal: zero means results are not out yet. */
    int countScoredBySeason(UUID seasonId);

    /** Nulls every result column, returning a season's rows to the waiting state. Dev preview only. */
    int clearResults(UUID seasonId);

    void deleteByUserId(UUID userId);
}
