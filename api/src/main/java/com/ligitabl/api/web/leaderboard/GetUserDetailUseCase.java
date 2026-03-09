package com.ligitabl.api.web.leaderboard;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.RoundResultRepo;
import com.ligitabl.model.repo.RoundSubmissionRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;
import com.ligitabl.model.repo.TeamRepo;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Web-layer use case that fetches user predictions for modal display.
 * Uses Either.catching to handle errors with exceptions.
 */
@Service("webGetUserDetailUseCase")
@RequiredArgsConstructor
@Slf4j
public class GetUserDetailUseCase {
    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final RoundSubmissionRepo roundSubmissionRepo;
    private final RoundResultRepo roundResultRepo;
    private final SeasonPredictionRepo seasonPredictionRepo;
    private final TeamRepo teamRepo;
    private final UserRepo userRepo;
    private final CompetitionDefaults competitionDefaults;

    public record UserPredictions(int round, List<PredictionTeam> predictions) {}

    public record PredictionTeam(String teamName, Integer hit) {}

    public Either<Exception, UserPredictions> execute(String publicId, Integer effectiveToRound) {
        return Either.catching(() -> {
            // Resolve competition
            Competition competition = competitionRepo
                    .findBySlug(competitionDefaults.defaultCompetitionSlug())
                    .orElseThrow(() -> new NotFoundException("Default competition not found"));

            // Resolve season
            Season season = seasonRepo
                    .findActiveSeason(competition.getId())
                    .orElseThrow(() -> new NotFoundException("Active season not found"));

            // Resolve current round
            Round currentRound = roundRepo
                    .findById(season.getCurrentRoundId())
                    .orElseThrow(() -> new NotFoundException("Current round not found"));

            // Resolve user by publicId
            User user = userRepo.findByPublicId(PublicId.create(publicId))
                    .orElseThrow(() -> new NotFoundException("User not found: " + publicId));

            // If we have an effective round (latest finalised within the currently viewed phase),
            // prefer showing the round result for that round (with per-team hits).
            Integer displayRound = effectiveToRound;
            List<PredictionTeam> predictions;

            if (effectiveToRound != null) {
                var submission = roundSubmissionRepo
                        .findByUserAndSeasonAndRound(user.getId(), season.getId(), effectiveToRound)
                        .orElseThrow(() -> new NotFoundException(
                                "No prediction found for user " + publicId + " at round " + effectiveToRound));

                var roundResult = roundResultRepo
                        .findByRoundSubmissionId(submission.getId())
                        .orElse(null);

                if (roundResult != null) {
                                        var resultRankings = roundResult.getRankings().stream()
                                                        .sorted(Comparator.comparingInt(r -> r.getRanking().getPosition()))
                                                        .toList();

                    var roundPredictions = mapRankingsToPredictionTeams(
                            resultRankings.stream().map(r -> r.getRanking()).toList());

                    predictions = IntStream.range(0, resultRankings.size())
                            .mapToObj(i -> new PredictionTeam(
                                    roundPredictions.get(i).teamName(),
                                    resultRankings.get(i).getHit()))
                            .toList();

                    return new UserPredictions(displayRound, predictions);
                }

                throw new NotFoundException(
                        "Round result not found for user " + publicId + " at round " + effectiveToRound);
            }

            // No finalized results available for the selected phase yet — show current prediction.
            SeasonPrediction prediction = seasonPredictionRepo
                    .findByUserAndSeason(user.getId(), season.getId())
                    .orElseThrow(() -> new NotFoundException("No prediction found for user " + publicId));

            predictions = mapRankingsToPredictionTeams(prediction.getCurrentRankings());
            displayRound = currentRound.getPosition();

            return new UserPredictions(displayRound, predictions);
        });
    }

    private List<PredictionTeam> mapRankingsToPredictionTeams(List<TeamRank> rankings) {
        var sortedRanks = rankings.stream()
                .sorted(Comparator.comparingInt(TeamRank::getPosition))
                .toList();

        Set<String> teamCodes = sortedRanks.stream().map(TeamRank::getCode).collect(Collectors.toSet());

        Map<String, Team> teamsByCode = teamRepo.findAllByCodes(teamCodes).stream()
                .collect(Collectors.toMap(Team::getCode, Function.identity()));

				return sortedRanks.stream()
                .map(tr -> {
                    Team team = teamsByCode.get(tr.getCode());
                    String name = team != null ? team.getShortName() : tr.getCode();
                    return new PredictionTeam(name, null);
                })
                .toList();
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }
}
