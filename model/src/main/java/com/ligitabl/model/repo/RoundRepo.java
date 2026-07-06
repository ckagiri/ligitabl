package com.ligitabl.model.repo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.Round;

public interface RoundRepo {

    Optional<Round> findById(UUID id);

    /** The position of the round with the given id, or {@code fallback} if not found (including a null id). */
    default int findPosition(UUID roundId, int fallback) {
        if (roundId == null) return fallback;
        return findById(roundId).map(Round::getPosition).orElse(fallback);
    }

    /** The position of the round with the given id, or {@code 0} if not found (including a null id). */
    default int findPosition(UUID roundId) {
        return findPosition(roundId, 0);
    }

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

    /**
     * The first (lowest-position) round in the season that isn't both finalized and advanced.
     * Used to guard season completion — every round must be fully closed out first.
     */
    Optional<Round> findFirstNotFinalizedOrAdvanced(UUID seasonId);
}
