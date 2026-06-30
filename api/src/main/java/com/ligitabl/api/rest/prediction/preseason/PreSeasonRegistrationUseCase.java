package com.ligitabl.api.rest.prediction.preseason;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.domain.Entry;
import com.ligitabl.model.domain.RoundSwap;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.SwapChange;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PreSeasonRegistrationUseCase {

    private static final int MAX_SWAPS = 5;
    private static final int ROUND_ZERO = 0;

    private final CompetitionDefaults competitionDefaults;
    private final SeasonRepo seasonRepo;
    private final SeasonPredictionRepo predictionRepo;
    private final ContestRepo contestRepo;
    private final EntryRepo entryRepo;

    @Transactional
    public Either<PreSeasonRegistrationError, PreSeasonRegistrationResult> execute(
            UUID userId, PreSeasonRegistrationCommand command) {

        return getActiveSeason()
                .flatMap(season -> validatePreSeason(season)
                        .flatMap(__ -> checkNotAlreadyRegistered(userId, season))
                        .flatMap(__ -> validateSwaps(command, season))
                        .flatMap(__ -> register(userId, season, command)));
    }

    private Either<PreSeasonRegistrationError, Season> getActiveSeason() {
        return seasonRepo
                .findActiveSeason(competitionDefaults.defaultCompetitionSlug())
                .map(Either::<PreSeasonRegistrationError, Season>right)
                .orElseGet(() -> Either.left(new PreSeasonRegistrationError.SeasonNotFound()));
    }

    private Either<PreSeasonRegistrationError, Void> validatePreSeason(Season season) {
        if (!season.isPreSeason()) {
            return Either.left(new PreSeasonRegistrationError.NotPreSeason());
        }
        return Either.right(null);
    }

    private Either<PreSeasonRegistrationError, Void> checkNotAlreadyRegistered(UUID userId, Season season) {
        return predictionRepo
                .findByUserAndSeason(userId, season.getId())
                .map(existing -> Either.<PreSeasonRegistrationError, Void>left(
                        new PreSeasonRegistrationError.AlreadyJoined(existing.getId())))
                .orElseGet(() -> Either.right(null));
    }

    private Either<PreSeasonRegistrationError, Void> validateSwaps(
            PreSeasonRegistrationCommand cmd, Season season) {
        List<PreSeasonRegistrationCommand.SwapPair> swaps = cmd.swaps();

        if (swaps.size() > MAX_SWAPS) {
            return Either.left(new PreSeasonRegistrationError.TooManySwaps(swaps.size(), MAX_SWAPS));
        }

        Set<String> validCodes =
                season.getInitialRankings().stream().map(TeamRank::getCode).collect(Collectors.toSet());

        for (PreSeasonRegistrationCommand.SwapPair swap : swaps) {
            String codeA = swap.teamACode().toUpperCase();
            String codeB = swap.teamBCode().toUpperCase();
            if (codeA.equals(codeB)) return Either.left(new PreSeasonRegistrationError.SameTeam());
            if (!validCodes.contains(codeA)) return Either.left(new PreSeasonRegistrationError.InvalidTeamCode(codeA));
            if (!validCodes.contains(codeB)) return Either.left(new PreSeasonRegistrationError.InvalidTeamCode(codeB));
        }

        return Either.right(null);
    }

    private Either<PreSeasonRegistrationError, PreSeasonRegistrationResult> register(
            UUID userId, Season season, PreSeasonRegistrationCommand command) {
        Contest mainContest = contestRepo.findById(season.getMainContestId()).orElse(null);
        if (mainContest == null) {
            return Either.left(new PreSeasonRegistrationError.MainContestNotFound());
        }

        try {
            Instant now = Instant.now();
            List<TeamRank> currentRankings = new ArrayList<>(season.getInitialRankings());
            List<SwapChange> swapChanges = new ArrayList<>();

            for (PreSeasonRegistrationCommand.SwapPair swap : command.swaps()) {
                String codeA = swap.teamACode().toUpperCase();
                String codeB = swap.teamBCode().toUpperCase();
                TeamRank rankA = findByCode(currentRankings, codeA);
                TeamRank rankB = findByCode(currentRankings, codeB);
                swapChanges.add(new SwapChange(
                        now,
                        String.format("%s:%d→%d", codeA, rankA.getPosition(), rankB.getPosition()),
                        String.format("%s:%d→%d", codeB, rankB.getPosition(), rankA.getPosition())));
                currentRankings = applySwap(currentRankings, codeA, codeB);
            }

            SeasonPrediction prediction = SeasonPrediction.builder()
                    .userId(userId)
                    .seasonId(season.getId())
                    .initialRankings(List.of())
                    .currentRankings(currentRankings)
                    .swaps(new ArrayList<>(List.of(new RoundSwap(ROUND_ZERO, swapChanges))))
                    .lastSwapAt(null)
                    .atRoundNumber(ROUND_ZERO)
                    .build();

            SeasonPrediction saved = predictionRepo.save(prediction);
            log.info("[PRE_SEASON_REGISTER] userId={} predictionId={} swaps={}", userId, saved.getId(), swapChanges.size());

            Entry entry = Entry.builder()
                    .userId(userId)
                    .contestId(mainContest.getId())
                    .joinedAtRound(ROUND_ZERO)
                    .build();
            Entry savedEntry = entryRepo.save(entry);

            return Either.right(new PreSeasonRegistrationResult(saved.getId(), savedEntry.getId(), swapChanges.size()));

        } catch (Exception e) {
            log.error("[PRE_SEASON_REGISTER] Failed for userId={}", userId, e);
            return Either.left(new PreSeasonRegistrationError.TransactionFailed(e.getMessage()));
        }
    }

    private TeamRank findByCode(List<TeamRank> rankings, String code) {
        return rankings.stream().filter(t -> t.getCode().equals(code)).findFirst().orElseThrow();
    }

    private List<TeamRank> applySwap(List<TeamRank> base, String codeA, String codeB) {
        List<TeamRank> result = new ArrayList<>(base);
        int idxA = IntStream.range(0, result.size())
                .filter(i -> result.get(i).getCode().equals(codeA))
                .findFirst()
                .orElseThrow();
        int idxB = IntStream.range(0, result.size())
                .filter(i -> result.get(i).getCode().equals(codeB))
                .findFirst()
                .orElseThrow();
        TeamRank a = result.get(idxA);
        TeamRank b = result.get(idxB);
        result.set(idxA, a.withPosition(b.getPosition()));
        result.set(idxB, b.withPosition(a.getPosition()));
        return result;
    }
}
