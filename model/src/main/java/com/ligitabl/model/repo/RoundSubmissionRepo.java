package com.ligitabl.model.repo;

import com.ligitabl.model.domain.RoundSubmission;

import java.util.List;
import java.util.Optional;

public interface RoundSubmissionRepo {
    RoundSubmission save(RoundSubmission submission);

    Optional<RoundSubmission> findById(Long id);

    Optional<RoundSubmission> findByUserAndSeasonAndRound(
            Long userId,
            Long seasonId,
            int roundPosition
    );

    List<RoundSubmission> findBySeasonAndRound(
            Long seasonId,
            int roundPosition
    );
}
