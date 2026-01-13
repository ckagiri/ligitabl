package com.ligitabl.api.usecases.match.matchadmin;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.usecases.shared.HierarchyValidator;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetMatchAdminDetailsUseCase
        implements UseCase<GetMatchAdminDetailsUseCase.Query, Either<UseCaseError, MatchAdminDetailsDto>> {

    private final MatchRepo matchRepo;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final HierarchyValidator hierarchyValidator;
    private final CompetitionDefaults competitionDefaults;

    @Override
    public Either<UseCaseError, MatchAdminDetailsDto> execute(Query query) {
        return resolveHierarchy(query)
                .flatMap(ctx -> findMatchInRound(ctx.round(), query.matchSlug())
                        .map(match -> new Context(ctx.season(), ctx.round(), match)))
                .map(ctx -> MatchAdminDetailsDto.builder()
                        .matchId(ctx.match().getId())
                        .matchSlug(ctx.match().getSlug())
                        .status(ctx.match().getStatus())
                        .roundPosition(ctx.round().getPosition())
                        .availableActions(actionsFor(ctx.season(), ctx.match()))
                        .build());
    }

    private Either<UseCaseError, HierarchyContext> resolveHierarchy(Query query) {
        String competitionIdentifier = query.competitionIdentifier() == null || query.competitionIdentifier().isBlank()
                ? competitionDefaults.defaultCompetitionSlug()
                : query.competitionIdentifier();

        return hierarchyValidator.validateCompetition(competitionIdentifier)
                .flatMap(this::getActiveSeason)
                .flatMap(season -> resolveRound(season, query)
                        .map(round -> new HierarchyContext(season, round)));
    }

    private Either<UseCaseError, Season> getActiveSeason(Competition competition) {
        UUID activeSeasonId = competition.getActiveSeasonId();
        if (activeSeasonId == null) {
            return Either.left(UseCaseErrors.validation("Competition has no active season"));
        }

        return requireFound(seasonRepo.findById(activeSeasonId), UseCaseErrors.notFound("Season", activeSeasonId));
    }

    private Either<UseCaseError, Round> resolveRound(Season season, Query query) {
        if (query.isCurrentRound()) {
            UUID currentRoundId = season.getCurrentRoundId();
            if (currentRoundId == null) {
                return Either.left(UseCaseErrors.validation("Season has no current round"));
            }
            return requireFound(roundRepo.findById(currentRoundId), UseCaseErrors.notFound("Round", currentRoundId));
        }

        return query.getRoundPositionAsNumber()
                .map(pos -> hierarchyValidator.validateRound(season.getId(), pos))
                .orElseGet(() -> Either.left(UseCaseErrors.validation("Invalid round position format")));
    }

    private Either<UseCaseError, Match> findMatchInRound(Round round, String matchSlug) {
        return requireFound(
                matchRepo.findByRoundIdAndSlug(round.getId(), matchSlug),
                UseCaseErrors.notFound("Match", "slug", matchSlug));
    }

    private static List<String> actionsFor(Season season, Match match) {
        boolean setup = season.isInSetupMode();
        MatchStatus status = match.getStatus();

        List<String> actions = new ArrayList<>();

        switch (status) {
            case SCHEDULED -> {
                actions.add("TRANSITION_TO_LIVE");
                actions.add("TRANSITION_TO_POSTPONED");
                actions.add("TRANSITION_TO_CANCELLED");
            }
            case LIVE -> {
                actions.add("TRANSITION_TO_SUSPENDED");
                actions.add("TRANSITION_TO_FINISHED");
                actions.add("TRANSITION_TO_CANCELLED");
            }
            case SUSPENDED -> {
                actions.add("TRANSITION_TO_POSTPONED");
                actions.add("TRANSITION_TO_CANCELLED");
            }
            case POSTPONED -> {
                actions.add("TRANSITION_TO_CANCELLED");
            }
            case CANCELLED, FINISHED -> {
                // No actions
            }
        }

        boolean reschedulable = setup || (status != MatchStatus.SUSPENDED && status != MatchStatus.LIVE && status != MatchStatus.FINISHED);
        if (reschedulable) {
            actions.add("RESCHEDULE");
        }

        return actions;
    }

    public record Query(String competitionIdentifier, String roundPosition, String matchSlug) {
        public boolean isCurrentRound() {
            return "current".equalsIgnoreCase(roundPosition);
        }

        public java.util.Optional<Integer> getRoundPositionAsNumber() {
            if (isCurrentRound()) {
                return java.util.Optional.empty();
            }
            try {
                return java.util.Optional.of(Integer.parseInt(roundPosition));
            } catch (NumberFormatException e) {
                return java.util.Optional.empty();
            }
        }
    }

    private record HierarchyContext(Season season, Round round) {}

    private record Context(Season season, Round round, Match match) {}
}
