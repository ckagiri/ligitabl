package com.ligitabl.model.repo;

import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.standings.Standings;

public interface StandingsRepo extends BaseCrudRepo<Standings, UUID> {
    Optional<Standings> findBySeasonAndRound(UUID seasonId, int roundPosition);
}
