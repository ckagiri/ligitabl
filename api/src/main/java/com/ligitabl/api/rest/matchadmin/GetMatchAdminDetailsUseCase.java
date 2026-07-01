package com.ligitabl.api.rest.matchadmin;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.rest.shared.HierarchyValidator.HierarchyContext;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.MatchRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GetMatchAdminDetailsUseCase
        implements UseCase<GetMatchAdminDetailsUseCase.Query, Either<UseCaseError, MatchAdminDetailsDto>> {

    private final MatchRepo matchRepo;
    private final HierarchyValidator hierarchyValidator;
    private final CompetitionDefaults competitionDefaults;

    @Override
    public Either<UseCaseError, MatchAdminDetailsDto> execute(Query query) {
        return resolveHierarchy(query)
                .flatMap(ctx -> findMatch(ctx.round(), query.matchSlug())
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
        String competitionIdentifier = query.competitionIdentifier() == null
                        || query.competitionIdentifier().isBlank()
                ? competitionDefaults.defaultCompetitionSlug()
                : query.competitionIdentifier();

        return hierarchyValidator.resolveHierarchy(competitionIdentifier, query.roundPosition());
    }

    private Either<UseCaseError, Match> findMatch(Round round, String matchSlug) {
        return requireFound(
                matchRepo.findByRoundIdAndSlug(round.getId(), matchSlug),
                UseCaseErrors.notFound("Match", "slug", matchSlug));
    }

    private static List<String> actionsFor(Season season, Match match) {
        boolean setup = season.isInSetupMode();
        MatchStatus status = match.getStatus();

        List<String> actions = new ArrayList<>();

        if (setup) {
            // Setup mode bypasses the normal match-day state machine — any other status is
            // reachable, same as Match.transitionTo(..., isSetupMode=true).
            for (MatchStatus target : MatchStatus.values()) {
                if (target != status) {
                    actions.add("TRANSITION_TO_" + target.name());
                }
            }
        } else {
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
                    actions.add("TRANSITION_TO_SCHEDULED");
                }
                case CANCELLED, FINISHED -> {
                    // No actions
                }
            }
        }

        boolean reschedulable = setup
                || (status != MatchStatus.SUSPENDED && status != MatchStatus.LIVE && status != MatchStatus.FINISHED);
        if (reschedulable) {
            actions.add("RESCHEDULE");
        }

        return actions;
    }

    public record Query(String competitionIdentifier, Integer roundPosition, String matchSlug) {}

    private record Context(Season season, Round round, Match match) {}
}
