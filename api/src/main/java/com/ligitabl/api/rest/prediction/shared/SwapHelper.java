package com.ligitabl.api.rest.prediction.shared;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.prediction.makeswap.SwapCommand;
import com.ligitabl.api.rest.prediction.makeswap.SwapError;
import com.ligitabl.api.rest.prediction.makeswap.TeamPair;
import com.ligitabl.api.rest.round.shared.RoundSupport;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.SwapChange;
import com.ligitabl.model.domain.TeamRank;
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
                    if (!season.isInPlay()) {
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

    public Either<SwapError, Void> validateRoundOpen(Round round) {
        if (round.isFinalized()) {
            return Either.left(new SwapError.RoundNotOpen(RoundStatus.COMPLETED.name()));
        }
        RoundStatus status = roundSupport.resolveStatus(round);
        return status == RoundStatus.OPEN ? Either.right(null) : Either.left(new SwapError.RoundNotOpen(status.name()));
    }

    public Set<String> resolveValidCodes(Season season, SeasonPrediction prediction) {
        List<TeamRank> initialRankings =
                season.getInitialRankings() != null ? season.getInitialRankings() : prediction.getInitialRankings();
        return initialRankings == null
                ? Set.of()
                : initialRankings.stream().map(TeamRank::getCode).collect(Collectors.toSet());
    }

    public Either<SwapError, TeamPair> validateTeams(
            SwapCommand command, Season season, SeasonPrediction prediction, List<TeamRank> rankings) {
        String teamACode = command.teamACode().toUpperCase();
        String teamBCode = command.teamBCode().toUpperCase();
        var validCodes = resolveValidCodes(season, prediction);
        if (!validCodes.contains(teamACode)) return Either.left(new SwapError.InvalidTeamCode(teamACode));
        if (!validCodes.contains(teamBCode)) return Either.left(new SwapError.InvalidTeamCode(teamBCode));
        TeamRank teamA = rankings.stream()
                .filter(t -> t.getCode().equals(teamACode))
                .findFirst()
                .orElse(null);
        TeamRank teamB = rankings.stream()
                .filter(t -> t.getCode().equals(teamBCode))
                .findFirst()
                .orElse(null);
        if (teamA == null || teamB == null) return Either.left(new SwapError.TeamsNotFound(teamACode, teamBCode));
        return Either.right(new TeamPair(teamA, teamB));
    }

    public SwapChange applySwap(List<TeamRank> rankings, TeamRank teamA, TeamRank teamB, Instant now) {
        int indexA = rankings.indexOf(teamA);
        int indexB = rankings.indexOf(teamB);

        TeamRank newTeamA = teamA.withPosition(teamB.getPosition());
        TeamRank newTeamB = teamB.withPosition(teamA.getPosition());

        rankings.set(indexA, newTeamA);
        rankings.set(indexB, newTeamB);

        return new SwapChange(
                now,
                String.format("%s:%d→%d", teamA.getCode(), teamA.getPosition(), newTeamA.getPosition()),
                String.format("%s:%d→%d", teamB.getCode(), teamB.getPosition(), newTeamB.getPosition()));
    }
}
