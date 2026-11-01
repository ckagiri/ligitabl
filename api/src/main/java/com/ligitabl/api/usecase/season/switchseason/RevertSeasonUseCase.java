package com.ligitabl.api.usecase.season.switchseason;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.model.domain.league.Competition;
import com.ligitabl.model.repo.CompetitionRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Quick undo for ActivateSeasonUseCase: restores formerSeasonId to activeSeasonId and demotes the
 * active season to upcomingSeasonId. Checks only that a former season exists.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RevertSeasonUseCase {

    private final CompetitionRepo competitionRepo;

    @Transactional
    public RevertSeasonResult execute(String competitionSlug) {
        Competition competition = competitionRepo.findBySlug(competitionSlug).orElse(null);
        if (competition == null) {
            return new RevertSeasonResult.CompetitionNotFound(competitionSlug);
        }

        UUID formerSeasonId = competition.getFormerSeasonId();
        if (formerSeasonId == null) {
            return new RevertSeasonResult.NoFormerSeason(competition.getId());
        }

        competitionRepo.revertToFormerSeason(competition.getId(), formerSeasonId, competition.getActiveSeasonId());

        log.info(
                "[ADMIN_REVERT_SEASON] competition={} newActiveSeasonId={} newUpcomingSeasonId={}",
                competitionSlug,
                formerSeasonId,
                competition.getActiveSeasonId());

        return new RevertSeasonResult.Ok(formerSeasonId);
    }
}
