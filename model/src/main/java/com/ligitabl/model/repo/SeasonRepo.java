package com.ligitabl.model.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;

public interface SeasonRepo {
    Optional<Season> findById(UUID id);

    List<Season> findAllByCompetitionId(UUID competitionId);  // Add this method

    Optional<Season> findByCompetitionIdAndSlug(UUID competitionId, SeasonSlug slug);

    boolean existsById(UUID id);
}
