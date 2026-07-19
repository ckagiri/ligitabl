package com.ligitabl.api.scheduling.advancematchday;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.client.FootballDataApiError;
import com.ligitabl.api.client.FootballDataClient;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdvanceMatchdayUseCase {

    private final FootballDataClient apiClient;
    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;
    private final CompetitionDefaults competitionDefaults;

    @Value("${football-data.competition.code}")
    private String competitionCode;

    public record AdvanceMatchdayCommand() {}

    public sealed interface AdvanceMatchdayError {
        record CompetitionNotFound(String code) implements AdvanceMatchdayError {}

        record SeasonNotFound(String competitionCode) implements AdvanceMatchdayError {}

        record ApiError(FootballDataApiError error) implements AdvanceMatchdayError {}
    }

    @Transactional
    public Either<AdvanceMatchdayError, MatchdayAdvancementResult> execute(AdvanceMatchdayCommand command) {
        log.info("Checking round advancement for competition: {}", competitionCode);

        return fetchCurrentMatchdayFromApi().flatMap(this::getActiveSeason).flatMap(this::advanceIfNeeded);
    }

    private Either<AdvanceMatchdayError, Integer> fetchCurrentMatchdayFromApi() {
        log.debug("Fetching current matchday from API");

        return apiClient
                .getCompetition(competitionCode)
                .mapLeft(error -> (AdvanceMatchdayError) new AdvanceMatchdayError.ApiError(error))
                .map(competition -> {
                    var currentMatchday = competition.currentSeason().currentMatchday();
                    log.debug("API reports current matchday: {}", currentMatchday);
                    return currentMatchday;
                });
    }

    private Either<AdvanceMatchdayError, SeasonContext> getActiveSeason(Integer apiMatchday) {
        var competitionSlug = competitionDefaults.defaultCompetitionSlug();

        if (competitionRepo.findBySlug(competitionSlug).isEmpty()) {
            return Either.left(new AdvanceMatchdayError.CompetitionNotFound(competitionCode));
        }

        return seasonRepo
                .findActiveSeason(competitionSlug)
                .map(season -> Either.<AdvanceMatchdayError, SeasonContext>right(new SeasonContext(season, apiMatchday)))
                .orElseGet(() -> Either.left(new AdvanceMatchdayError.SeasonNotFound(competitionCode)));
    }

    private Either<AdvanceMatchdayError, MatchdayAdvancementResult> advanceIfNeeded(SeasonContext context) {

        var currentMatchday = context.season().getCurrentMatchDay();
        var apiMatchday = context.apiMatchday();

        if (apiMatchday.equals(currentMatchday)) {
            log.info("Matchday unchanged: {}", currentMatchday);
            return Either.right(MatchdayAdvancementResult.noChange(
                    context.season().getId(), currentMatchday, "API matchday matches current matchday"));
        }

        if (apiMatchday < currentMatchday) {
            log.warn(
                    "API matchday ({}) is behind current matchday ({}). "
                            + "This shouldn't happen in most cases - possible API issue or scheduling rollover",
                    apiMatchday,
                    currentMatchday);
            return Either.right(MatchdayAdvancementResult.noChange(
                    context.season().getId(),
                    currentMatchday,
                    "API matchday is behind current matchday - no action taken"));
        }

        // API matchday is ahead - advance
        log.info("Advancing matchday from {} to {}", currentMatchday, apiMatchday);

        var season = context.season();
        season.setCurrentMatchDay(apiMatchday);
        seasonRepo.save(season);

        return Either.right(MatchdayAdvancementResult.advanced(context.season().getId(), currentMatchday, apiMatchday));
    }

    private record SeasonContext(Season season, Integer apiMatchday) {}
}
