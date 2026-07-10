package com.ligitabl.model.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.RoundResult;

public interface RoundResultRepo {
    RoundResult save(RoundResult result);

    Optional<RoundResult> findByRoundSubmissionId(UUID submissionId);

    List<RoundResult> findBySeasonAndRoundPositionRange(UUID seasonId, int fromRound, int toRound);

    List<RoundResult> findByUserAndSeasonAndRoundPositionRange(UUID userId, UUID seasonId, int fromRound, int toRound);

    Optional<RoundResult> findByUserAndRound(UUID uuid, int roundPosition);

    Optional<RoundResult> findLatestByUserAndSeason(UUID userId, UUID seasonId);
}
