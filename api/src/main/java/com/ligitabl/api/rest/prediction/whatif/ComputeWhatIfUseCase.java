package com.ligitabl.api.rest.prediction.whatif;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.domain.StandingsCalculatorService;
import com.ligitabl.api.rest.round.shared.RoundSupport;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.*;
import com.ligitabl.model.domain.standings.StandingsConverter;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.TeamRepo;

import lombok.RequiredArgsConstructor;

/**
 * Simulates the round's standings table (and, by extension, a user's score against it) from
 * hypothetical match scores, without persisting anything. Reuses {@link StandingsCalculatorService}
 * unmodified by feeding it a synthetic match list (real finished matches from earlier rounds, plus
 * the current round's matches carrying the submitted hypothetical scores) instead of a DB-fetched one.
 */
@Component
@RequiredArgsConstructor
public class ComputeWhatIfUseCase {

    private final CompetitionDefaults competitionDefaults;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final MatchRepo matchRepo;
    private final TeamRepo teamRepo;
    private final RoundSupport roundSupport;
    private final StandingsCalculatorService standingsCalculatorService;

    private record Ctx(Season season, Round round, List<Match> roundMatches) {}

    private record ScoredCtx(Ctx ctx, Map<UUID, WhatIfScore> scoresByMatchId) {}

    public Either<WhatIfError, WhatIfResult> execute(WhatIfCommand command) {
        return getActiveSeason()
                .flatMap(season -> getCurrentRound(season).map(round -> new Ctx(season, round, List.of())))
                .flatMap(ctx -> validateRoundOpen(ctx.round()).map(__ -> ctx))
                .map(ctx -> new Ctx(
                        ctx.season(), ctx.round(), matchRepo.findByRoundIdWithTeams(ctx.round().getId())))
                .flatMap(ctx -> validateScores(ctx.roundMatches(), command).map(scores -> new ScoredCtx(ctx, scores)))
                .flatMap(scoredCtx -> calculateWhatIfStandings(scoredCtx.ctx(), scoredCtx.scoresByMatchId()));
    }

    private Either<WhatIfError, Season> getActiveSeason() {
        return seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .map(Either::<WhatIfError, Season>right)
                .orElseGet(() -> Either.left(new WhatIfError.SeasonNotFound(null)))
                .flatMap(season -> {
                    if (season.isCompleted()) {
                        return Either.left(new WhatIfError.SeasonCompleted());
                    }
                    if (!season.isInPlay()) {
                        return Either.left(new WhatIfError.SeasonNotInPlay(season.getId()));
                    }
                    if (season.isInSetupMode()) {
                        return Either.left(new WhatIfError.SeasonInSetupMode());
                    }
                    return Either.right(season);
                });
    }

    private Either<WhatIfError, Round> getCurrentRound(Season season) {
        UUID roundId = season.getCurrentRoundId();
        if (roundId == null) {
            return Either.left(new WhatIfError.RoundNotFound(season.getId()));
        }
        return roundRepo
                .findById(roundId)
                .map(Either::<WhatIfError, Round>right)
                .orElseGet(() -> Either.left(new WhatIfError.RoundNotFound(season.getId())));
    }

    private Either<WhatIfError, Round> validateRoundOpen(Round round) {
        RoundStatus status = roundSupport.resolveStatus(round);
        return status == RoundStatus.OPEN
                ? Either.right(round)
                : Either.left(new WhatIfError.RoundNotOpen(status.name()));
    }

    /**
     * Only matches still SCHEDULED are scoreable — POSTPONED/CANCELLED/SUSPENDED matches contribute
     * nothing to a real result either, so they're excluded from both the "every match needs a score"
     * requirement and the synthetic match list built afterward.
     */
    private Either<WhatIfError, Map<UUID, WhatIfScore>> validateScores(List<Match> roundMatches, WhatIfCommand command) {
        Set<UUID> scoreableIds = roundMatches.stream()
                .filter(m -> m.getStatus() == MatchStatus.SCHEDULED)
                .map(Match::getId)
                .collect(Collectors.toSet());

        Map<UUID, WhatIfScore> byMatchId = new LinkedHashMap<>();
        for (WhatIfScore score : command.scores()) {
            byMatchId.put(score.matchId(), score);
        }

        List<UUID> unknown =
                byMatchId.keySet().stream().filter(id -> !scoreableIds.contains(id)).toList();
        if (!unknown.isEmpty()) {
            return Either.left(new WhatIfError.UnknownMatch(unknown));
        }

        List<UUID> missing =
                scoreableIds.stream().filter(id -> !byMatchId.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            return Either.left(new WhatIfError.MissingScores(missing));
        }

        for (WhatIfScore score : byMatchId.values()) {
            if (score.homeGoals() < 0 || score.awayGoals() < 0) {
                return Either.left(new WhatIfError.InvalidScore(score.matchId(), "Goals cannot be negative"));
            }
        }

        return Either.right(byMatchId);
    }

    private Either<WhatIfError, WhatIfResult> calculateWhatIfStandings(
            Ctx ctx, Map<UUID, WhatIfScore> scoresByMatchId) {
        List<Match> priorFinished = matchRepo
                .findFinishedMatchesUpToRoundWithTeams(ctx.season().getId(), ctx.round().getPosition())
                .stream()
                .filter(m -> !m.getRoundId().equals(ctx.round().getId()))
                .toList();

        Map<UUID, Match> roundMatchesById =
                ctx.roundMatches().stream().collect(Collectors.toMap(Match::getId, m -> m));

        List<Match> hypothetical = scoresByMatchId.values().stream()
                .map(score -> buildSyntheticMatch(roundMatchesById.get(score.matchId()), score))
                .toList();

        List<Match> synthetic = new ArrayList<>(priorFinished);
        synthetic.addAll(hypothetical);

        Set<String> teamCodes = ctx.season().getInitialRankings().stream()
                .map(TeamRank::getCode)
                .collect(Collectors.toSet());
        List<Team> teams = teamRepo.findAllByCodes(teamCodes);

        return standingsCalculatorService
                .calculateStandings(synthetic, teams)
                .mapLeft(this::mapStandingsError)
                .map(standings -> new WhatIfResult(StandingsConverter.convert(standings, teams)));
    }

    private Match buildSyntheticMatch(Match original, WhatIfScore score) {
        return Match.builder()
                .id(original.getId())
                .homeTeamId(original.getHomeTeamId())
                .awayTeamId(original.getAwayTeamId())
                .status(MatchStatus.FINISHED)
                .score(Score.builder()
                        .homeGoals(score.homeGoals())
                        .awayGoals(score.awayGoals())
                        .build())
                .build();
    }

    private WhatIfError mapStandingsError(StandingsCalculatorService.StandingsError error) {
        return switch (error) {
            case StandingsCalculatorService.StandingsError.CalculationFailed e ->
                new WhatIfError.CalculationFailed(e.message());
            default -> new WhatIfError.CalculationFailed("Standings calculation failed");
        };
    }
}
