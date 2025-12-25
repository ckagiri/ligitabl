package com.ligitabl.model.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;

public interface SeasonRepo {
    Optional<Season> findById(UUID id);

    Season save(Season season);

    Optional<Season> findActiveSeason(String competitionSlug);

    List<Season> findAllByCompetitionId(UUID competitionId);

    Optional<Season> findByCompetitionIdAndSlug(UUID competitionId, SeasonSlug slug);

    default Optional<Season> findBySlug(UUID competitionId, String slug) {
        return findByCompetitionIdAndSlug(competitionId, SeasonSlug.of(slug));
    }

    Optional<Season> findByClientId(Integer clientId);

    boolean existsById(UUID id);
}
