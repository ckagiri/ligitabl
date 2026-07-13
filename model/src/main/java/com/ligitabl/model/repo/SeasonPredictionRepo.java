package com.ligitabl.model.repo;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.SeasonPrediction;

public interface SeasonPredictionRepo {
    Optional<SeasonPrediction> findByUserAndSeason(UUID userId, UUID seasonId);

    boolean existsByUserAndSeason(UUID userId, UUID seasonId);

    SeasonPrediction save(SeasonPrediction prediction);

    List<SeasonPrediction> findBySeasonAndAtRoundNumberLessThanEqual(UUID seasonId, int roundNumber);

    /** All-time count of season predictions per user, across every season. */
    Map<UUID, Integer> countByUserIds(Collection<UUID> userIds);

    /** Total swap count per user, scoped to a single season. */
    Map<UUID, Integer> sumSwapCountsByUserIdsAndSeason(Collection<UUID> userIds, UUID seasonId);

    void deleteByUserId(UUID userId);
}
