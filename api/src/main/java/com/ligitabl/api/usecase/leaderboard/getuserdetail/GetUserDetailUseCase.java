package com.ligitabl.api.usecase.leaderboard.getuserdetail;

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
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.model.domain.identity.PublicId;
import com.ligitabl.model.domain.identity.User;
import com.ligitabl.model.domain.league.Round;
import com.ligitabl.model.domain.league.Season;
import com.ligitabl.model.domain.league.Team;
import com.ligitabl.model.domain.league.TeamRank;
import com.ligitabl.model.domain.prediction.RoundResult;
import com.ligitabl.model.domain.prediction.SeasonPrediction;
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
 * Fetches a user's predictions for the leaderboard modal.
 */
@Service
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

    public Either<UseCaseError, UserPredictions> execute(String publicId, Integer effectiveToRound, UUID seasonId) {
        var competition = competitionRepo
                .findBySlug(competitionDefaults.defaultCompetitionSlug())
                .orElse(null);
        if (competition == null) return Either.left(UseCaseErrors.notFound("Default competition not found"));

        // Resolve season: prefer the contest's own season when provided, otherwise
        // fall back to the globally active season (e.g. when opened from /leaderboard).
        Season season = (seasonId != null
                        ? seasonRepo.findById(seasonId)
                        : seasonRepo.findActiveSeason(competition.getId()))
                .orElse(null);
        if (season == null) return Either.left(UseCaseErrors.notFound("Season not found"));

        Round currentRound = roundRepo.findById(season.getCurrentRoundId()).orElse(null);
        if (currentRound == null) return Either.left(UseCaseErrors.notFound("Current round not found"));

        return findUser(publicId)
                .flatMap(user -> buildUserPredictions(user, publicId, effectiveToRound, season, currentRound));
    }

    private Either<UseCaseError, User> findUser(String publicId) {
        UseCaseError notFound = UseCaseErrors.notFound("User not found: " + publicId);
        return Either.catching(() -> PublicId.create(publicId), notFound)
                .flatMap(id -> Either.ofOptional(userRepo.findByPublicId(id), notFound));
    }

    private Either<UseCaseError, UserPredictions> buildUserPredictions(
            User user, String publicId, Integer effectiveToRound, Season season, Round currentRound) {
        // Only for the "View full prediction" link; a season without a slug just yields no link.
        String seasonSlug = season.getSlug() != null ? season.getSlug().toShorthand() : null;

        // If a finalized round result exists for the effective round, prefer that.
        var fromResult = Optional.ofNullable(effectiveToRound)
                .flatMap(round -> roundSubmissionRepo.findByUserAndSeasonAndRound(user.getId(), season.getId(), round))
                .flatMap(submission -> roundResultRepo.findByRoundSubmissionId(submission.getId()))
                .map(roundResult -> buildUserPredictionsFromResult(effectiveToRound, roundResult, seasonSlug));
        if (fromResult.isPresent()) return Either.right(fromResult.get());

        int displayRound = effectiveToRound != null ? effectiveToRound : currentRound.getPosition();

        // Past round with no finalized data
        if (effectiveToRound != null && effectiveToRound < currentRound.getPosition()) {
            return Either.right(new UserPredictions(displayRound, null, List.of(), seasonSlug));
        }

        // Only fall back to live prediction when viewing the current round
        return Either.<UseCaseError, SeasonPrediction>ofOptional(
                        seasonPredictionRepo.findByUserAndSeason(user.getId(), season.getId()),
                        () -> UseCaseErrors.notFound("No prediction found for user " + publicId))
                .map(prediction -> new UserPredictions(
                        displayRound, null, mapRankingsToPredictionTeams(prediction.getCurrentRankings()), seasonSlug));
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
                    // shorterName: the modal truncates a narrow column, so the tightest readable
                    // name wins. Falls back through shortName to the code.
                    String name = team != null
                            ? firstNonBlank(team.getShorterName(), team.getShortName(), tr.getCode())
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
}
