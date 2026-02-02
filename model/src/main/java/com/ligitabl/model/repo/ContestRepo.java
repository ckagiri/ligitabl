package com.ligitabl.model.repo;

import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.Contest;

public interface ContestRepo {
    Optional<Contest> findById(UUID id);

    Contest save(Contest contest);

    Optional<Contest> findMainBySeasonId(UUID seasonId);

    boolean existsByUserAndContest(UUID userId, UUID contestId);
}
