package com.ligitabl.api.rest.standings;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.Standings;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.StandingsRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetDefaultRoundStandingsUseCase
        implements UseCase<GetDefaultRoundStandingsQuery, Either<UseCaseError, RoundStandingsResult>> {

    private final HierarchyValidator hierarchyValidator;
    private final CompetitionDefaults competitionDefaults;
    private final RoundRepo roundRepo;
    private final StandingsRepo standingsRepo;
    private final StandingsEnricher standingsEnricher;

    @Override
    public Either<UseCaseError, RoundStandingsResult> execute(GetDefaultRoundStandingsQuery query) {
        String competitionIdentifier = getEffectiveCompetitionIdentifier(query);

        return hierarchyValidator
                .resolveHierarchy(competitionIdentifier, query.getRoundPosition())
                .flatMap(ctx -> resolveCurrentRound(ctx.season())
                        .flatMap(currentRound -> fetchStandings(ctx.season(), ctx.round())
                                .flatMap(standingsEnricher::enrichWithTeams)
                                .map(standings -> new RoundStandingsResult(
                                        ctx.season().getId(),
                                        ctx.round().getPosition(),
                                        currentRound.getPosition(),
                                        ctx.season().getMaxRounds(),
                                        standings))));
    }

    private Either<UseCaseError, Round> resolveCurrentRound(Season season) {
        if (season.getCurrentRoundId() == null) {
            return Either.left(UseCaseErrors.validation("Season has no current round"));
        }

        return requireFound(
                roundRepo.findById(season.getCurrentRoundId()),
                UseCaseErrors.notFound("Round", season.getCurrentRoundId()));
    }

    private String getEffectiveCompetitionIdentifier(GetDefaultRoundStandingsQuery query) {
        return query.getCompetitionSlug() != null
                ? query.getCompetitionSlug()
                : competitionDefaults.defaultCompetitionSlug();
    }

    private Either<UseCaseError, Standings> fetchStandings(Season season, Round round) {
        return Either.catching(
                () -> standingsRepo
                        .findBySeasonAndRoundPosition(season.getId(), round.getPosition())
                        .orElseGet(() -> {
                            log.debug(
                                    "No standings found for season={} round={}, returning empty",
                                    season.getId(),
                                    round.getPosition());
                            return Standings.builder()
                                    .seasonId(season.getId())
                                    .roundPosition(round.getPosition())
                                    .rankings(List.of())
                                    .finalised(false)
                                    .build();
                        }),
                UseCaseErrors::fromException);
    }
}
