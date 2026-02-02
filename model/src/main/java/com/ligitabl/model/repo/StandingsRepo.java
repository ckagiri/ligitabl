package com.ligitabl.model.repo;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.Standings;

public interface StandingsRepo extends BaseCrudRepo<Standings, UUID> {
    Standings save(Standings standings);

    Optional<Standings> findBySeasonAndRoundPosition(UUID seasonId, int roundPosition);

    Map<String, Integer> findPositionMap(UUID seasonId, int roundPosition);

    Map<String, Integer> findPointsMap(UUID seasonId, int roundPosition);

    Optional<Standings> findLatestBySeason(UUID seasonId);
}
