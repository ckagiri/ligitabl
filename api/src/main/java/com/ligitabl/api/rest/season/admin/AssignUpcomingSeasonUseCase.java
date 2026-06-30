package com.ligitabl.api.rest.season.admin;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Assigns a season as the competition's upcoming season, ready for ActivateSeasonUseCase to promote later. */
@Component
@RequiredArgsConstructor
@Slf4j
public class AssignUpcomingSeasonUseCase {

    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;

    public sealed interface Result
            permits Result.Ok,
                    Result.CompetitionNotFound,
                    Result.SeasonNotFound,
                    Result.SeasonNotBelongToCompetition,
                    Result.SeasonAlreadyCompleted {
        record Ok(UUID seasonId) implements Result {}

        record CompetitionNotFound(String slug) implements Result {}

        record SeasonNotFound(UUID seasonId) implements Result {}

        record SeasonNotBelongToCompetition(UUID seasonId, UUID competitionId) implements Result {}

        record SeasonAlreadyCompleted(UUID seasonId) implements Result {}
    }

    @Transactional
    public Result execute(String competitionSlug, UUID seasonId) {
        Competition competition = competitionRepo.findBySlug(competitionSlug).orElse(null);
        if (competition == null) {
            return new Result.CompetitionNotFound(competitionSlug);
        }

        Season season = seasonRepo.findById(seasonId).orElse(null);
        if (season == null) {
            return new Result.SeasonNotFound(seasonId);
        }

        if (!season.getCompetitionId().equals(competition.getId())) {
            return new Result.SeasonNotBelongToCompetition(seasonId, competition.getId());
        }

        if (season.isCompleted()) {
            return new Result.SeasonAlreadyCompleted(seasonId);
        }

        competitionRepo.assignUpcomingSeason(competition.getId(), seasonId);

        log.info("[ADMIN_ASSIGN_UPCOMING_SEASON] competition={} upcomingSeasonId={}", competitionSlug, seasonId);

        return new Result.Ok(seasonId);
    }
}
