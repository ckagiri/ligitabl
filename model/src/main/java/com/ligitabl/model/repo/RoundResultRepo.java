package com.ligitabl.model.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.RoundResult;

public interface RoundResultRepo {
    RoundResult save(RoundResult result);

    Optional<RoundResult> findByRoundSubmissionId(UUID submissionId);

    List<RoundResult> findBySeasonAndRoundPositionRange(UUID seasonId, int fromRound, int toRound);
}
