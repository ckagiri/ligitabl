package com.ligitabl.api.runners.calcstandings;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;
import static java.time.OffsetDateTime.now;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.domain.StandingsCalculatorService;
import com.ligitabl.api.domain.StandingsCalculatorService.StandingsError;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.rest.standings.StandingsEnricher;
import com.ligitabl.api.rest.standings.StandingsEntryDto;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.StandingsRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CalculateRoundStandingsUseCase
        implements UseCase<CalculateRoundStandingsCommand, Either<UseCaseError, List<StandingsEntryDto>>> {

    private final HierarchyValidator hierarchyValidator;
    private final CompetitionDefaults competitionDefaults;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final MatchRepo matchRepo;
    private final StandingsRepo standingsRepo;
    private final StandingsEnricher standingsEnricher;

    private final StandingsCalculatorService standingsCalculator;

    @Override
    public Either<UseCaseError, List<StandingsEntryDto>> execute(CalculateRoundStandingsCommand command) {
        String competitionSlug = resolveCompetitionSlug(command);

        return hierarchyValidator
                .validateCompetition(competitionSlug)
                .flatMap(this::getActiveSeason)
                .flatMap(season -> resolveRound(season, command))
                .flatMap(context -> calcAndSaveStandings(context.season(), context.round()))
                .flatMap(standings -> standingsEnricher.enrichWithTeams(standings));
    }

    private String resolveCompetitionSlug(CalculateRoundStandingsCommand command) {
        return command.getCompetitionSlug() != null
                ? command.getCompetitionSlug()
                : competitionDefaults.defaultCompetitionSlug();
    }

    private Either<UseCaseError, Season> getActiveSeason(Competition competition) {
        UUID activeSeasonId = competition.getActiveSeasonId();
        if (activeSeasonId == null) {
            return Either.left(UseCaseErrors.validation("Competition has no active season"));
        }

        return requireFound(seasonRepo.findById(activeSeasonId), UseCaseErrors.notFound("Season", activeSeasonId));
    }

    private Either<UseCaseError, RoundContext> resolveRound(Season season, CalculateRoundStandingsCommand command) {
        if (command.isCurrentRound()) {
            return getCurrentRound(season).map(round -> new RoundContext(season, round));
        } else {
            return getRoundByPosition(season, command.getRoundPosition()).map(round -> new RoundContext(season, round));
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

    private Either<UseCaseError, Standings> calcAndSaveStandings(Season season, Round round) {
        // Check if round is already finalized
        if (round.isFinalized()) {
            return Either.left(UseCaseErrors.validation("Round already finalized"));
        }

        // Get matches for this round to check if there are any finished matches
        List<Match> matches = matchRepo.findByRoundId(round.getId());

        RoundStatus status = round.computeStatus(matches);
        boolean shouldFinalise = status == RoundStatus.COMPLETED;

        boolean hasFinishedMatches = matches.stream().anyMatch(match -> match.getStatus() == MatchStatus.FINISHED);

        if (!hasFinishedMatches) {
            return Either.left(UseCaseErrors.validation("No finished matches in round " + round.getPosition()));
        }

        var calculation = standingsCalculator
                .calculateRankings(season.getId(), round.getPosition())
                .mapLeft(this::standingsErrorToUseCaseError);

        if (calculation.isLeft()) {
            return Either.left(calculation.getLeft());
        }

        List<StandingsTeamRank> rankings = calculation.get();

        // Get or create standings record
        Standings standings = standingsRepo
                .findBySeasonAndRoundPosition(season.getId(), round.getPosition())
                .orElseGet(() -> Standings.builder()
                        .seasonId(season.getId())
                        .roundPosition(round.getPosition())
                        .rankings(List.of())
                        .finalised(false)
                        .build());

        if (standings.isFinalised()) {
            return Either.left(UseCaseErrors.validation("Standings already finalised"));
        }

        standings.setRankings(rankings);

        if (shouldFinalise) {
            standings.setFinalised(true);
            standings.setFinalisedAt(now());
        } else {
            standings.setFinalised(false);
            standings.setFinalisedAt(null);
        }

        return Either.right(standingsRepo.save(standings));
    }

    private UseCaseError standingsErrorToUseCaseError(StandingsError error) {
        return switch (error) {
            case StandingsError.SeasonNotFound e -> UseCaseErrors.notFound("Season", e.seasonId());
            case StandingsError.NoInitialRankings e -> UseCaseErrors.validation(
                    "Season has no initial rankings: " + e.seasonId());
            case StandingsError.TeamsNotFound e -> UseCaseErrors.notFound("Team", e.codes());
            case StandingsError.CalculationFailed e -> UseCaseErrors.validation(
                    "Standings calculation failed: " + e.message());
        };
    }

    private record RoundContext(Season season, Round round) {}
}
