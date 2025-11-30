package com.ligitabl.model.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.Round;

public interface RoundRepo {

    List<Round> findBySeasonId(UUID seasonId);

    Optional<Round> findBySeasonIdAndPosition(UUID seasonId, int position);
}
