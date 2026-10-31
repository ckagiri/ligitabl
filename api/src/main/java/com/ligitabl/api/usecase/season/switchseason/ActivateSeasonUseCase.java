package com.ligitabl.api.usecase.season.switchseason;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.model.domain.league.Competition;
import com.ligitabl.model.domain.league.Season;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ActivateSeasonUseCase {

    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;

    /** Promotes competition.upcomingSeasonId to activeSeasonId, demoting the current active season to former. */
    @Transactional
    public ActivateSeasonResult execute(String competitionSlug, OffsetDateTime predictionsOpenAt) {
        Competition competition = competitionRepo.findBySlug(competitionSlug).orElse(null);
        if (competition == null) {
            return new ActivateSeasonResult.CompetitionNotFound(competitionSlug);
        }

        UUID upcomingSeasonId = competition.getUpcomingSeasonId();
        if (upcomingSeasonId == null) {
            return new ActivateSeasonResult.NoUpcomingSeason(competition.getId());
        }

        if (predictionsOpenAt != null) {
            Season upcoming = seasonRepo.findById(upcomingSeasonId).orElse(null);
            if (upcoming != null) {
                upcoming.schedulePredictionsOpening(predictionsOpenAt);
                seasonRepo.save(upcoming);
            }
        }

        competitionRepo.promoteUpcomingSeason(competition.getId(), upcomingSeasonId, competition.getActiveSeasonId());

        log.info(
                "[ADMIN_ACTIVATE_SEASON] competition={} newActiveSeasonId={} formerSeasonId={} predictionsOpenAt={}",
                competitionSlug,
                upcomingSeasonId,
                competition.getActiveSeasonId(),
                predictionsOpenAt);

        return new ActivateSeasonResult.Ok(upcomingSeasonId);
    }
}
