package com.ligitabl.model.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;

public interface SeasonRepo {
    Optional<Season> findById(UUID id);

    Season save(Season season);

    Optional<Season> findActiveSeason(String competitionSlugOrCode);

    /**
     * Returns the most recent season for a competition, regardless of completion status.
     * Useful for use cases that need to surface "season completed" rather than "not found".
     */
    Optional<Season> findMostRecentSeason(String competitionSlug);

    List<Season> findAllByCompetitionId(UUID competitionId);

    Optional<Season> findByCompetitionIdAndSlug(UUID competitionId, SeasonSlug slug);

    default Optional<Season> findBySlug(UUID competitionId, String slug) {
        return findByCompetitionIdAndSlug(competitionId, SeasonSlug.of(slug));
    }

    Optional<Season> findByClientId(Integer clientId);

    boolean existsById(UUID id);

    Optional<Season> findActiveSeason(UUID competitionId);

    /**
     * Finds the upcoming (not yet active) season for a competition: an incomplete season
     * whose id differs from activeSeasonId. Used by SeasonActivationService.
     */
    Optional<Season> findUpcomingSeason(UUID competitionId, UUID activeSeasonId);
}
