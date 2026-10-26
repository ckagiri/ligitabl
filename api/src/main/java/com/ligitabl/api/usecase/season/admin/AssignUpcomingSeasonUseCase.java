package com.ligitabl.api.usecase.season.admin;

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

    @Transactional
    public AssignUpcomingSeasonResult execute(String competitionSlug, UUID seasonId) {
        Competition competition = competitionRepo.findBySlug(competitionSlug).orElse(null);
        if (competition == null) {
            return new AssignUpcomingSeasonResult.CompetitionNotFound(competitionSlug);
        }

        Season season = seasonRepo.findById(seasonId).orElse(null);
        if (season == null) {
            return new AssignUpcomingSeasonResult.SeasonNotFound(seasonId);
        }

        if (!season.getCompetitionId().equals(competition.getId())) {
            return new AssignUpcomingSeasonResult.SeasonNotBelongToCompetition(seasonId, competition.getId());
        }

        if (season.isCompleted()) {
            return new AssignUpcomingSeasonResult.SeasonAlreadyCompleted(seasonId);
        }

        competitionRepo.assignUpcomingSeason(competition.getId(), seasonId);

        log.info("[ADMIN_ASSIGN_UPCOMING_SEASON] competition={} upcomingSeasonId={}", competitionSlug, seasonId);

        return new AssignUpcomingSeasonResult.Ok(seasonId);
    }
}
