package com.ligitabl.api.rest.standings;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.Standings;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.StandingsRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetDefaultRoundStandingsUseCase
        implements UseCase<GetDefaultRoundStandingsQuery, Either<UseCaseError, List<StandingsEntryDto>>> {

    private final HierarchyValidator hierarchyValidator;
    private final CompetitionDefaults competitionDefaults;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final StandingsRepo standingsRepo;
    private final StandingsEnricher standingsEnricher;

    @Override
    public Either<UseCaseError, List<StandingsEntryDto>> execute(GetDefaultRoundStandingsQuery query) {
        String competitionSlug = resolveCompetitionSlug(query);

        return hierarchyValidator
                .validateCompetition(competitionSlug)
                .flatMap(this::getActiveSeason)
                .flatMap(season -> resolveRound(season, query))
                .flatMap(context -> fetchStandings(context.season(), context.round()))
                .flatMap(standingsEnricher::enrichWithTeams);
    }

    private String resolveCompetitionSlug(GetDefaultRoundStandingsQuery query) {
        return query.getCompetitionSlug() != null
                ? query.getCompetitionSlug()
                : competitionDefaults.defaultCompetitionSlug();
    }

    private Either<UseCaseError, Season> getActiveSeason(Competition competition) {
        UUID activeSeasonId = competition.getActiveSeasonId();
        if (activeSeasonId == null) {
            return Either.left(UseCaseErrors.validation("Competition has no active season"));
        }

        return requireFound(seasonRepo.findById(activeSeasonId), UseCaseErrors.notFound("Season", activeSeasonId));
    }

    private Either<UseCaseError, RoundContext> resolveRound(Season season, GetDefaultRoundStandingsQuery query) {
        if (query.isCurrentRound()) {
            return getCurrentRound(season).map(round -> new RoundContext(season, round));
        } else {
            return getRoundByPosition(season, query.getRoundPosition()).map(round -> new RoundContext(season, round));
        }
    }

    private Either<UseCaseError, Round> getCurrentRound(Season season) {
        UUID currentRoundId = season.getCurrentRoundId();
        if (currentRoundId == null) {
            return Either.left(UseCaseErrors.validation("Season has no current round"));
        }

        return requireFound(roundRepo.findById(currentRoundId), UseCaseErrors.notFound("Round", currentRoundId));
    }

    private Either<UseCaseError, Round> getRoundByPosition(Season season, Integer position) {
        if (position == null || position < 1) {
            return Either.left(UseCaseErrors.validation("Round position must be at least 1"));
        }

        if (position > season.getMaxRounds()) {
            return Either.left(UseCaseErrors.validation(
                    String.format("Round position %d exceeds max rounds %d", position, season.getMaxRounds())));
        }

        return requireFound(
                roundRepo.findBySeasonIdAndPosition(season.getId(), position),
                UseCaseErrors.notFound("Round", "position", String.valueOf(position)));
    }

    private Either<UseCaseError, Standings> fetchStandings(Season season, Round round) {
        return standingsRepo
                .findBySeasonAndRoundPosition(season.getId(), round.getPosition())
                .map(Either::<UseCaseError, Standings>right)
                .orElseGet(() -> {
                    log.debug(
                            "No standings found for season={} round={}, returning empty",
                            season.getId(),
                            round.getPosition());
                    // Return empty standings instead of error
                    return Either.right(Standings.builder()
                            .seasonId(season.getId())
                            .roundPosition(round.getPosition())
                            .rankings(List.of())
                            .finalised(false)
                            .build());
                });
    }

    private record RoundContext(Season season, Round round) {}
}
