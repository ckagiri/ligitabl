package com.ligitabl.model.repo;

import com.ligitabl.model.domain.Contest;

import java.util.Optional;
import java.util.UUID;

public interface ContestRepo {
    Optional<Contest> findById(UUID id);
    Contest save(Contest contest);
    Optional<Contest> findDefaultContestBySeason(UUID seasonId);
}
