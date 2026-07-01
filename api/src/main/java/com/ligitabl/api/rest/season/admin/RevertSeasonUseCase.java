package com.ligitabl.api.rest.season.admin;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.repo.CompetitionRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Quick undo for ActivateSeasonUseCase: restores formerSeasonId back to activeSeasonId,
 * demoting the current active season to upcomingSeasonId. No date/eligibility checks
 * beyond "a former season exists" — this is a quick undo, not a guarded transition.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RevertSeasonUseCase {

    private final CompetitionRepo competitionRepo;

    public sealed interface Result permits Result.Ok, Result.CompetitionNotFound, Result.NoFormerSeason {
        record Ok(UUID newActiveSeasonId) implements Result {}

        record CompetitionNotFound(String slug) implements Result {}

        record NoFormerSeason(UUID competitionId) implements Result {}
    }

    @Transactional
    public Result execute(String competitionSlug) {
        Competition competition = competitionRepo.findBySlug(competitionSlug).orElse(null);
        if (competition == null) {
            return new Result.CompetitionNotFound(competitionSlug);
        }

        UUID formerSeasonId = competition.getFormerSeasonId();
        if (formerSeasonId == null) {
            return new Result.NoFormerSeason(competition.getId());
        }

        competitionRepo.revertToFormerSeason(competition.getId(), formerSeasonId, competition.getActiveSeasonId());

        log.info(
                "[ADMIN_REVERT_SEASON] competition={} newActiveSeasonId={} newUpcomingSeasonId={}",
                competitionSlug,
                formerSeasonId,
                competition.getActiveSeasonId());

        return new Result.Ok(formerSeasonId);
    }
}
