package com.ligitabl.model.repo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.Round;

public interface RoundRepo {

    Optional<Round> findById(UUID id);

    List<Round> findBySeasonId(UUID seasonId);

    Optional<Round> findBySeasonIdAndPosition(UUID seasonId, int position);

    List<Round> findBySeasonIdOrderByPosition(UUID id);

    Round save(Round round);

    /**
     * Finds finalized, not-yet-advanced rounds whose scheduled advancement
     * time has passed. Used by RoundAdvancementRecovery on startup.
     */
    List<Round> findMissedAdvancements(OffsetDateTime now);

    /**
     * Bulk-marks rounds unfinalized for a season, between the given positions inclusive.
     * Used by the setup-mode refinalize cascade to flag downstream rounds as out of sync.
     */
    void markUnfinalizedBetween(UUID seasonId, int fromPositionInclusive, int toPositionInclusive);
}
