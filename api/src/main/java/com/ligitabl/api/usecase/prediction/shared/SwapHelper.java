package com.ligitabl.api.usecase.prediction.shared;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.usecase.prediction.makeswap.SwapCommand;
import com.ligitabl.api.usecase.prediction.makeswap.SwapError;
import com.ligitabl.api.usecase.round.shared.RoundSupport;
import com.ligitabl.model.domain.league.Round;
import com.ligitabl.model.domain.league.RoundStatus;
import com.ligitabl.model.domain.league.Season;
import com.ligitabl.model.domain.league.TeamRank;
import com.ligitabl.model.domain.prediction.SeasonPrediction;
import com.ligitabl.model.domain.prediction.SwapChange;
import com.ligitabl.model.domain.prediction.SwapEligibility;
import com.ligitabl.model.domain.prediction.TeamSwap;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class SwapHelper {

    private final CompetitionDefaults competitionDefaults;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final SeasonPredictionRepo predictionRepo;
    private final RoundSupport roundSupport;
    private final Clock clock;

    public Either<SwapError, Season> getActiveSeason() {
        return seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .map(Either::<SwapError, Season>right)
                .orElseGet(() -> {
                    log.warn("No active season found for competition {}", competitionDefaults.defaultCompetitionSlug());
                    return Either.left(new SwapError.SeasonNotFound(null));
                })
                .flatMap(season -> {
                    if (season.isCompleted()) {
                        log.info("Season {} is completed, rejecting swap", season.getId());
                        return Either.left(new SwapError.SeasonCompleted());
                    }
                    if (!season.isInPlay(clock.instant())) {
                        log.info("Season {} is not in play, rejecting swap", season.getId());
                        return Either.left(new SwapError.SeasonNotInPlay(season.getId()));
                    }
                    if (season.isInSetupMode()) {
                        log.info("Season {} is in setup mode, rejecting swap", season.getId());
                        return Either.left(new SwapError.SeasonInSetupMode());
                    }
                    return Either.right(season);
                });
    }

    public Either<SwapError, Round> getCurrentRound(Season season) {
        return roundRepo
                .findById(season.getCurrentRoundId())
                .map(Either::<SwapError, Round>right)
                .orElseGet(() -> Either.left(new SwapError.RoundNotOpen(RoundStatus.UNKNOWN.name())))
                .flatMap(round -> isLastRoundClosed(round, season)
                        ? Either.left(new SwapError.RoundNotOpen(
                                resolveClosedStatus(round).name()))
                        : Either.right(round));
    }

    private boolean isLastRoundClosed(Round round, Season season) {
        return round.getPosition() == season.getMaxRounds() && (round.isFinalized() || round.isAdvanced());
    }

    private RoundStatus resolveClosedStatus(Round round) {
        return round.isAdvanced() ? RoundStatus.ADVANCED : RoundStatus.FINALIZED;
    }

    public Either<SwapError, SeasonPrediction> getPrediction(UUID userId, UUID seasonId) {
        return predictionRepo
                .findByUserAndSeason(userId, seasonId)
                .map(Either::<SwapError, SeasonPrediction>right)
                .orElseGet(() -> Either.left(new SwapError.NoPredictionFound(userId, seasonId)));
    }

    /** COMPLETED once finalized, whatever its matches say. */
    public RoundStatus roundStatus(Round round) {
        return round.isFinalized() ? RoundStatus.COMPLETED : roundSupport.resolveStatus(round);
    }

    public Either<SwapError, Void> validateEligibility(SwapEligibility eligibility, int round, Instant now) {
        return switch (eligibility) {
            case SwapEligibility.Allowed allowed -> Either.right(null);
            case SwapEligibility.RoundNotOpen notOpen -> Either.left(
                    new SwapError.RoundNotOpen(notOpen.status().name()));
            case SwapEligibility.PreSeasonMergeRequired p -> Either.left(new SwapError.PreSeasonMergeRequired(round));
            case SwapEligibility.UseOpeningWindowFirst u -> Either.left(new SwapError.UseOpeningWindowFirst(round));
            case SwapEligibility.OpeningAlreadyUsed o -> Either.left(new SwapError.OpeningAlreadyUsed(round));
            case SwapEligibility.CooldownActive cooldown -> Either.left(new SwapError.CooldownActive(
                    cooldown.nextSwapAt(),
                    Duration.between(now, cooldown.nextSwapAt()).toMinutes() / 60.0));
        };
    }

    public Set<String> resolveValidCodes(Season season, SeasonPrediction prediction) {
        List<TeamRank> initialRankings =
                season.getInitialRankings() != null ? season.getInitialRankings() : prediction.getInitialRankings();
        return initialRankings == null
                ? Set.of()
                : initialRankings.stream().map(TeamRank::getCode).collect(Collectors.toSet());
    }

    /** Checks both codes belong to the season and the prediction. Swaps only permute positions, so a batch can check every pair up front. */
    public Either<SwapError, TeamSwap> validateTeams(SwapCommand command, Season season, SeasonPrediction prediction) {
        String teamACode = command.teamACode().toUpperCase();
        String teamBCode = command.teamBCode().toUpperCase();
        var validCodes = resolveValidCodes(season, prediction);
        if (!validCodes.contains(teamACode)) return Either.left(new SwapError.InvalidTeamCode(teamACode));
        if (!validCodes.contains(teamBCode)) return Either.left(new SwapError.InvalidTeamCode(teamBCode));
        var predictionCodes =
                prediction.getCurrentRankings().stream().map(TeamRank::getCode).collect(Collectors.toSet());
        if (!predictionCodes.contains(teamACode) || !predictionCodes.contains(teamBCode)) {
            return Either.left(new SwapError.TeamsNotFound(teamACode, teamBCode));
        }
        return Either.right(new TeamSwap(teamACode, teamBCode));
    }

    public SwapChange applySwap(List<TeamRank> rankings, TeamRank teamA, TeamRank teamB, Instant now) {
        return SwapChange.exchange(rankings, teamA, teamB, now);
    }
}
