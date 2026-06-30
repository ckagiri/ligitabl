package com.ligitabl.api.rest.season.admin;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Season;
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

    public sealed interface Result permits Result.Ok, Result.CompetitionNotFound, Result.SeasonNotFound, Result.SeasonNotBelongToCompetition {
        record Ok(UUID newActiveSeasonId) implements Result {}
        record CompetitionNotFound(String slug) implements Result {}
        record SeasonNotFound(UUID seasonId) implements Result {}
        record SeasonNotBelongToCompetition(UUID seasonId, UUID competitionId) implements Result {}
    }

    @Transactional
    public Result execute(String competitionSlug, UUID seasonId, OffsetDateTime predictionsOpenAt) {
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

        if (predictionsOpenAt != null) {
            season.setPredictionsOpenAt(predictionsOpenAt);
            seasonRepo.save(season);
        }

        competitionRepo.updateActiveSeasonId(competition.getId(), seasonId);

        log.info("[ADMIN_ACTIVATE_SEASON] competition={} newActiveSeasonId={} predictionsOpenAt={}",
                competitionSlug, seasonId, predictionsOpenAt);

        return new Result.Ok(seasonId);
    }
}
