package com.ligitabl.model.repo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;

public interface CompetitionRepo {
    Optional<Competition> findById(UUID id);

    List<Competition> findAll();

    Optional<Competition> findBySlug(CompetitionSlug slug);

    Optional<Competition> findByCode(String code);

    Optional<Competition> findBySlugOrCode(String identifier);

    default Optional<Competition> findBySlug(String slug) {
        return findBySlug(CompetitionSlug.of(slug));
    }

    boolean existsById(UUID id);

    /** Assigns a season as the competition's upcoming season (does not touch active/former). */
    void assignUpcomingSeason(UUID competitionId, UUID seasonId);

    /** Promotes upcomingSeasonId to activeSeasonId, demotes the old active season to formerSeasonId, clears upcoming. */
    void promoteUpcomingSeason(UUID competitionId, UUID newActiveSeasonId, UUID newFormerSeasonId);

    /** Reverts formerSeasonId back to activeSeasonId, demotes the current active season to upcomingSeasonId, clears former. */
    void revertToFormerSeason(UUID competitionId, UUID newActiveSeasonId, UUID newUpcomingSeasonId);
}
