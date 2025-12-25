package com.ligitabl.model.repo;

import com.ligitabl.model.domain.SeasonPrediction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeasonPredictionRepo {
    Optional<SeasonPrediction> findByUserAndSeason(UUID userId, UUID seasonId);
    SeasonPrediction save(SeasonPrediction prediction);
    List<SeasonPrediction> findBySeasonAndAtRoundNumberLessThanEqual(
            Long seasonId,
            int roundNumber
    );
}
