package com.ligitabl.api.web.leaderboard;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Service;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.RoundResult;
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

    public record UserPredictions(int round, Integer roundScore, List<PredictionTeam> predictions, String seasonSlug) {}

    public record PredictionTeam(String teamName, Integer hit) {}

    public Either<Exception, UserPredictions> execute(String publicId, Integer effectiveToRound, UUID seasonId) {
        return Either.catching(() -> {
            // Resolve competition
            Competition competition = competitionRepo
                    .findBySlug(competitionDefaults.defaultCompetitionSlug())
                    .orElseThrow(() -> new NotFoundException("Default competition not found"));

            // Resolve season: prefer the contest's own season when provided, otherwise
            // fall back to the globally active season (e.g. when opened from /leaderboard).
            Season season = (seasonId != null
                            ? seasonRepo.findById(seasonId)
                            : seasonRepo.findActiveSeason(competition.getId()))
                    .orElseThrow(() -> new NotFoundException("Season not found"));

            // Resolve current round
            Round currentRound = roundRepo
                    .findById(season.getCurrentRoundId())
                    .orElseThrow(() -> new NotFoundException("Current round not found"));

            // Resolve user by publicId
            User user = userRepo.findByPublicId(PublicId.create(publicId))
                    .orElseThrow(() -> new NotFoundException("User not found: " + publicId));

            // Only used to build the "View full prediction" deep link; a season without a slug
            // simply yields no link rather than failing the whole modal.
            String seasonSlug = season.getSlug() != null ? season.getSlug().toShorthand() : null;

            // If a finalized round result exists for the effective round, prefer that.
            return Optional.ofNullable(effectiveToRound)
                    .flatMap(round ->
                            roundSubmissionRepo.findByUserAndSeasonAndRound(user.getId(), season.getId(), round))
                    .flatMap(submission -> roundResultRepo.findByRoundSubmissionId(submission.getId()))
                    .map(roundResult -> buildUserPredictionsFromResult(effectiveToRound, roundResult, seasonSlug))
                    .orElseGet(() -> {
                        int displayRound = effectiveToRound != null ? effectiveToRound : currentRound.getPosition();

                        // Only fall back to live prediction when viewing the current round
                        if (effectiveToRound == null || effectiveToRound >= currentRound.getPosition()) {
                            SeasonPrediction prediction = seasonPredictionRepo
                                    .findByUserAndSeason(user.getId(), season.getId())
                                    .orElseThrow(
                                            () -> new NotFoundException("No prediction found for user " + publicId));
                            return new UserPredictions(
                                    displayRound,
                                    null,
                                    mapRankingsToPredictionTeams(prediction.getCurrentRankings()),
                                    seasonSlug);
                        }

                        // Past round with no finalized data
                        return new UserPredictions(displayRound, null, List.of(), seasonSlug);
                    });
        });
    }

    private UserPredictions buildUserPredictionsFromResult(
            Integer displayRound, RoundResult roundResult, String seasonSlug) {
        var resultRankings = roundResult.getRankings().stream()
                .sorted(Comparator.comparingInt(r -> r.getRanking().getPosition()))
                .toList();

        var roundPredictions = mapRankingsToPredictionTeams(
                resultRankings.stream().map(r -> r.getRanking()).toList());

        var predictions = IntStream.range(0, resultRankings.size())
                .mapToObj(i -> new PredictionTeam(
                        roundPredictions.get(i).teamName(),
                        resultRankings.get(i).getHit()))
                .toList();

        return new UserPredictions(displayRound, roundResult.getTotalScore(), predictions, seasonSlug);
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
                    // shorterName over shortName: the modal lists 20 rows in a narrow column and
                    // truncates, so the tightest name that still reads is the useful one. Falls
                    // back through shortName to the code when a team has no shorter form.
                    String name = team != null ? firstNonBlank(team.getShorterName(), team.getShortName(), tr.getCode())
                                               : tr.getCode();
                    return new PredictionTeam(name, null);
                })
                .toList();
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) return candidate;
        }
        return null;
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }
}
