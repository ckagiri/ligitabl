package com.ligitabl.api.usecases.sync;

import com.ligitabl.api.client.FootballDataClient;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.scheduling.sync.RoundAdvancementResult;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.SeasonRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdvanceRoundUseCase {

    private final FootballDataClient apiClient;
    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;

    @Value("${football-data.competition.code}")
    private String competitionCode;
    private CompetitionDefaults competitionDefaults;

    public AdvanceRoundUseCase(
            FootballDataClient apiClient,
            CompetitionRepo competitionRepo,
            SeasonRepo seasonRepo, CompetitionDefaults competitionDefaults) {
        this.apiClient = apiClient;
        this.competitionRepo = competitionRepo;
        this.seasonRepo = seasonRepo;
        this.competitionDefaults = competitionDefaults;
    }

    public record AdvanceRoundCommand() {}

    public sealed interface AdvanceRoundError {
        record CompetitionNotFound(String code) implements AdvanceRoundError {}
        record SeasonNotFound(String competitionCode) implements AdvanceRoundError {}
        record ApiError(FootballDataClient.ApiError error) implements AdvanceRoundError {}
    }

    @Transactional
    public Either<AdvanceRoundError, RoundAdvancementResult> execute(AdvanceRoundCommand command) {
        log.info("Checking round advancement for competition: {}", competitionCode);

        return fetchCurrentMatchdayFromApi()
                .flatMap(this::getActiveSeason)
                .flatMap(this::advanceIfNeeded);
    }

    private Either<AdvanceRoundError, Integer> fetchCurrentMatchdayFromApi() {
        log.debug("Fetching current matchday from API");

        return apiClient.getCompetition(competitionCode)
                .mapLeft(AdvanceRoundError.ApiError::new)
                .map(competition -> {
                    var currentMatchday = competition.currentSeason().currentMatchday();
                    log.debug("API reports current matchday: {}", currentMatchday);
                    return currentMatchday;
                });
    }

    private Either<AdvanceRoundError, SeasonContext> getActiveSeason(Integer apiMatchday) {
        return seasonRepo.findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                        .map(season -> Either.<AdvanceRoundError, SeasonContext>right(
                                new SeasonContext(season, apiMatchday)
                        ))
                        .orElse(Either.left(new AdvanceRoundError.SeasonNotFound(competitionCode))
                )
                .orElse(Either.left(new AdvanceRoundError.CompetitionNotFound(competitionCode)));
    }

    private Either<AdvanceRoundError, RoundAdvancementResult> advanceIfNeeded(
            SeasonContext context) {

        var currentMatchday = context.season().getCurrentMatchDay();
        var apiMatchday = context.apiMatchday();

        if (apiMatchday.equals(currentMatchday)) {
            log.info("Matchday unchanged: {}", currentMatchday);
            return Either.right(RoundAdvancementResult.noChange(
                    context.season().getId(),
                    currentMatchday,
                    "API matchday matches current matchday"
            ));
        }

        if (apiMatchday < currentMatchday) {
            log.warn("API matchday ({}) is behind current matchday ({}). " +
                            "This shouldn't happen in most cases - possible API issue or season rollover",
                    apiMatchday, currentMatchday);
            return Either.right(RoundAdvancementResult.noChange(
                    context.season().getId(),
                    currentMatchday,
                    "API matchday is behind current matchday - no action taken"
            ));
        }

        // API matchday is ahead - advance
        log.info("Advancing matchday from {} to {}", currentMatchday, apiMatchday);

        seasonRepo.updateCurrentMatchday(context.season().getId(), apiMatchday);

        return Either.right(RoundAdvancementResult.advanced(
                context.season().getId(),
                currentMatchday,
                apiMatchday
        ));
    }

    private record SeasonContext(
            Season season,
            Integer apiMatchday
    ) {}
}
