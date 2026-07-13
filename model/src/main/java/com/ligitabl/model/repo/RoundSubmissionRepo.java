package com.ligitabl.model.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.RoundSubmission;

public interface RoundSubmissionRepo {
    RoundSubmission save(RoundSubmission submission);

    Optional<RoundSubmission> findById(UUID id);

    Optional<RoundSubmission> findByUserAndSeasonAndRound(UUID userId, UUID seasonId, int roundPosition);

    List<RoundSubmission> findBySeasonAndRound(UUID seasonId, int roundPosition);

    boolean existsByRound(UUID seasonId, int roundPosition);

    boolean existsBySeasonId(UUID seasonId);

    void deleteByUserId(UUID userId);
}
