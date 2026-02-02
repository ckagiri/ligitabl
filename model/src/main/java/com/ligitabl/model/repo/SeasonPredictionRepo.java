package com.ligitabl.model.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.SeasonPrediction;

public interface SeasonPredictionRepo {
    Optional<SeasonPrediction> findByUserAndSeason(UUID userId, UUID seasonId);

    boolean existsByUserAndSeason(UUID userId, UUID seasonId);

    SeasonPrediction save(SeasonPrediction prediction);

    List<SeasonPrediction> findBySeasonAndAtRoundNumberLessThanEqual(UUID seasonId, int roundNumber);
}
