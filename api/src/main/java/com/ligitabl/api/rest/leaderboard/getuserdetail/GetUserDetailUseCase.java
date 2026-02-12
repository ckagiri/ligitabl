package com.ligitabl.api.rest.leaderboard.getuserdetail;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.leaderboard.getleaderboard.GetLeaderboardError;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.LeaderboardEntry;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundSpan;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.LeaderboardRepo;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.RoundSubmissionRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.TeamRepo;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetUserDetailUseCase {
    private final LeaderboardRepo leaderboardRepo;
    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;
    private final ContestRepo contestRepo;
    private final RoundRepo roundRepo;
    private final MatchRepo matchRepo;
    private final RoundSubmissionRepo roundSubmissionRepo;
    private final SeasonPredictionRepo seasonPredictionRepo;
    private final TeamRepo teamRepo;
    private final UserRepo userRepo;
    private final CompetitionDefaults competitionDefaults;

    public Either<GetUserDetailError, GetUserDetailResult> execute(GetUserDetailQuery query) {
        var competition = competitionRepo
                .findBySlug(competitionDefaults.defaultCompetitionSlug())
                .orElse(null);
        if (competition == null) {
            return Either.left(
                    new GetUserDetailError.LeaderboardError(new GetLeaderboardError.DefaultCompetitionNotFound()));
        }

        if (competition.getPhases() == null || competition.getPhases().isEmpty()) {
            return Either.left(new GetUserDetailError.LeaderboardError(new GetLeaderboardError.PhasesNotConfigured()));
        }

        var season = seasonRepo.findActiveSeason(competition.getId()).orElse(null);
        if (season == null) {
            return Either.left(
                    new GetUserDetailError.LeaderboardError(new GetLeaderboardError.ActiveSeasonNotFound()));
        }

        var contest = contestRepo.findMainBySeasonId(season.getId()).orElse(null);
        if (contest == null) {
            return Either.left(new GetUserDetailError.LeaderboardError(new GetLeaderboardError.MainContestNotFound()));
        }

        if (season.getCurrentRoundId() == null) {
            return Either.left(new GetUserDetailError.CurrentRoundNotFound());
        }

        Round currentRound = roundRepo.findById(season.getCurrentRoundId()).orElse(null);
        if (currentRound == null) {
            return Either.left(new GetUserDetailError.CurrentRoundNotFound());
        }

        var phase = findPhase(competition, query.phase());
        if (phase == null) {
            return Either.left(
                    new GetUserDetailError.LeaderboardError(new GetLeaderboardError.InvalidPhase(query.phase())));
        }

        // Resolve user by publicId
        var user = userRepo.findByPublicId(PublicId.create(query.publicId())).orElse(null);
        if (user == null) {
            return Either.left(new GetUserDetailError.UserNotFound(query.publicId()));
        }

        // Resolve effective round (latest finalized)
        Integer effectiveRound =
                leaderboardRepo.resolveEffectiveToRound(season.getId(), phase.getFrom(), phase.getTo());
        if (effectiveRound == null) {
            return Either.left(new GetUserDetailError.NoFinalizedRounds());
        }

        // Compute leaderboard and find user's entry
        var leaderboardResponse = leaderboardRepo.computeLeaderboard(
                contest.getId(), season.getId(), phase.getFrom(), phase.getTo(), user.getId(), 0, 1);

        LeaderboardEntry userEntry = leaderboardResponse.userEntry();
        if (userEntry == null) {
            return Either.left(new GetUserDetailError.UserNotFound(query.publicId()));
        }

        List<TeamRank> rankings;
        List<Match> matches = matchRepo.findByRoundId(currentRound.getId());
        RoundStatus currentRoundStatus =
                matches == null || matches.isEmpty() ? RoundStatus.OPEN : currentRound.computeStatus(matches);

        if (currentRoundStatus == RoundStatus.LOCKED) {
            var submission = roundSubmissionRepo
                    .findByUserAndSeasonAndRound(user.getId(), season.getId(), effectiveRound)
                    .orElse(null);
            if (submission == null) {
                return Either.left(new GetUserDetailError.NoPredictionFound(query.publicId(), effectiveRound));
            }
            rankings = submission.getRankings();
        } else {
            var prediction = seasonPredictionRepo
                    .findByUserAndSeason(user.getId(), season.getId())
                    .orElse(null);
            if (prediction == null) {
                return Either.left(
                        new GetUserDetailError.NoPredictionFound(query.publicId(), currentRound.getPosition()));
            }
            rankings = prediction.getCurrentRankings();
        }

        // Resolve team codes to names
        List<TeamRank> sortedRanks = rankings.stream()
                .sorted(Comparator.comparingInt(TeamRank::getPosition))
                .toList();

        Set<String> teamCodes = sortedRanks.stream().map(TeamRank::getCode).collect(Collectors.toSet());

        Map<String, Team> teamsByCode = teamRepo.findAllByCodes(teamCodes).stream()
                .collect(Collectors.toMap(Team::getCode, Function.identity()));

        List<GetUserDetailResult.PredictionTeam> predictions = sortedRanks.stream()
                .map(tr -> {
                    Team team = teamsByCode.get(tr.getCode());
                    String name = team != null ? team.getName() : tr.getCode();
                    return new GetUserDetailResult.PredictionTeam(name);
                })
                .toList();

        // Determine if showing a previous round (effective < current round means current round isn't finalized yet)
        boolean showingPreviousRound = effectiveRound < currentRound.getPosition();

        return Either.right(new GetUserDetailResult(
                userEntry.displayName(),
                userEntry.position(),
                userEntry.totalScore(),
                userEntry.roundScore(),
                effectiveRound,
                showingPreviousRound,
                predictions));
    }

    private RoundSpan findPhase(Competition competition, String phaseCode) {
        var code = phaseCode != null ? phaseCode : "FS";
        if (competition.getPhases() == null) {
            return null;
        }
        return competition.getPhases().stream()
                .filter(p -> p.getCode().equalsIgnoreCase(code))
                .findFirst()
                .orElse(null);
    }
}
