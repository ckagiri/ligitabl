package com.ligitabl.model.repo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.Standings;
import com.ligitabl.model.domain.StandingsTeamRank;

public interface StandingsRepo extends BaseCrudRepo<Standings, UUID> {
    Standings save(Standings standings);

    Optional<Standings> findBySeasonAndRoundPosition(UUID seasonId, int roundPosition);

    Map<String, Integer> findPositionMap(UUID seasonId, int roundPosition);

    Map<String, Integer> findPointsMap(UUID seasonId, int roundPosition);

    Map<String, Integer> findGoalDifferenceMap(UUID seasonId, int roundPosition);

    Optional<Standings> findLatestBySeason(UUID seasonId);

    /**
     * Bulk-marks standings unfinalised for a season, between the given round positions inclusive,
     * clearing finalisedAt. Used by the setup-mode refinalize cascade to flag downstream rounds as out of sync.
     */
    void markUnfinalisedBetween(UUID seasonId, int fromPositionInclusive, int toPositionInclusive);

    /**
     * Replaces only the rankings of an existing standings row, leaving finalised/finalisedAt untouched.
     * Recalculation paths must use this rather than save(), so a stale in-memory snapshot can never
     * overwrite a concurrent finalization's flags.
     */
    Standings updateRankings(UUID id, List<StandingsTeamRank> rankings);
}
