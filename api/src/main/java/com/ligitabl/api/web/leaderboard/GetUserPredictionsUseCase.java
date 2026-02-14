package com.ligitabl.api.web.leaderboard;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.Team;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.CompetitionRepo;
import com.ligitabl.model.repo.MatchRepo;
import com.ligitabl.model.repo.RoundRepo;
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
@Service
@RequiredArgsConstructor
@Slf4j
public class GetUserPredictionsUseCase {
    private final CompetitionRepo competitionRepo;
    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
    private final MatchRepo matchRepo;
    private final RoundSubmissionRepo roundSubmissionRepo;
    private final SeasonPredictionRepo seasonPredictionRepo;
    private final TeamRepo teamRepo;
    private final UserRepo userRepo;
    private final CompetitionDefaults competitionDefaults;

    public record UserPredictions(int round, List<PredictionTeam> predictions) {}

    public record PredictionTeam(String teamName) {}

    public Either<Exception, UserPredictions> execute(String publicId) {
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
            User user = userRepo
                    .findByPublicId(PublicId.create(publicId))
                    .orElseThrow(() -> new NotFoundException("User not found: " + publicId));

            // Determine round status
            List<Match> matches = matchRepo.findByRoundId(currentRound.getId());
            RoundStatus currentRoundStatus =
                    matches == null || matches.isEmpty() ? RoundStatus.OPEN : currentRound.computeStatus(matches);

            // Fetch rankings based on round status
            List<TeamRank> rankings;
            int displayRound;

            if (currentRoundStatus == RoundStatus.LOCKED) {
                // Show previous round's finalized submission
                int previousRound = currentRound.getPosition() - 1;
                if (previousRound < 1) {
                    throw new NotFoundException("No finalized rounds available yet");
                }

                var submission = roundSubmissionRepo
                        .findByUserAndSeasonAndRound(user.getId(), season.getId(), previousRound)
                        .orElseThrow(() -> new NotFoundException(
                                "No prediction found for user " + publicId + " at round " + previousRound));

                rankings = submission.getRankings();
                displayRound = previousRound;
            } else {
                // Show current prediction
                SeasonPrediction prediction = seasonPredictionRepo
                        .findByUserAndSeason(user.getId(), season.getId())
                        .orElseThrow(() -> new NotFoundException("No prediction found for user " + publicId));

                rankings = prediction.getCurrentRankings();
                displayRound = currentRound.getPosition();
            }

            // Resolve team codes to names
            List<TeamRank> sortedRanks =
                    rankings.stream().sorted(Comparator.comparingInt(TeamRank::getPosition)).toList();

            Set<String> teamCodes =
                    sortedRanks.stream().map(TeamRank::getCode).collect(Collectors.toSet());

            Map<String, Team> teamsByCode = teamRepo.findAllByCodes(teamCodes).stream()
                    .collect(Collectors.toMap(Team::getCode, Function.identity()));

            List<PredictionTeam> predictions = sortedRanks.stream()
                    .map(tr -> {
                        Team team = teamsByCode.get(tr.getCode());
                        String name = team != null ? team.getName() : tr.getCode();
                        return new PredictionTeam(name);
                    })
                    .toList();

            return new UserPredictions(displayRound, predictions);
        });
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }
}
