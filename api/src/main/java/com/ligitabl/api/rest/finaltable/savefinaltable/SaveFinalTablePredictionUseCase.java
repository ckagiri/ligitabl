package com.ligitabl.api.rest.finaltable.savefinaltable;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ligitabl.api.rest.finaltable.savefinaltable.SaveFinalTableCommand.SwapPair;
import com.ligitabl.api.rest.finaltable.shared.FinalTableError;
import com.ligitabl.api.rest.finaltable.shared.FinalTableSupport;
import com.ligitabl.api.rest.prediction.shared.SwapHelper;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.FinalTablePrediction;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SwapChange;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.FinalTablePredictionRepo;

import lombok.RequiredArgsConstructor;

/**
 * Saves a batch of swaps by replaying them server-side.
 *
 * <p>The client sends the pairs it tapped, in order, plus the order it expects to land on. The stored
 * table comes from replaying the pairs — {@code expectedOrder} is only a checksum, so a mismatch
 * means another tab has saved in between and the client must reload.
 */
@Component
@RequiredArgsConstructor
public class SaveFinalTablePredictionUseCase {

    private final FinalTablePredictionRepo predictionRepo;
    private final FinalTableSupport finalTableSupport;
    private final SwapHelper swapHelper;
    private final Clock clock;

    private record Ctx(Season season, FinalTablePrediction prediction, boolean existed) {}

    public Either<FinalTableError, FinalTablePrediction> execute(UUID userId, SaveFinalTableCommand command) {
        return finalTableSupport
                .activeSeason()
                .flatMap(this::requireEntryOpen)
                .flatMap(season -> loadOrCreate(userId, season))
                .flatMap(ctx -> requireSomethingToSave(ctx, command))
                .flatMap(ctx -> replay(ctx, command));
    }

    private Either<FinalTableError, Season> requireEntryOpen(Season season) {
        return finalTableSupport.isEntryOpen(season)
                ? Either.right(season)
                : Either.left(new FinalTableError.EntryClosed(
                        finalTableSupport.entryStatus(season).name()));
    }

    /** A first-time player's row is built from the season baseline rather than refused. */
    private Either<FinalTableError, Ctx> loadOrCreate(UUID userId, Season season) {
        return predictionRepo
                .findByUserAndSeason(userId, season.getId())
                .map(existing -> existing.isScored()
                        ? Either.<FinalTableError, Ctx>left(new FinalTableError.AlreadyScored())
                        : Either.<FinalTableError, Ctx>right(new Ctx(season, existing, true)))
                .orElseGet(() -> {
                    Instant now = clock.instant();
                    FinalTablePrediction fresh = FinalTablePrediction.builder()
                            .userId(userId)
                            .seasonId(season.getId())
                            .rankings(new ArrayList<>(finalTableSupport.baselineRankings(season)))
                            .settledAt(now)
                            .build();
                    return Either.right(new Ctx(season, fresh, false));
                });
    }

    /**
     * An empty batch is legal only as the baseline-accepting first save. Checked on row existence
     * rather than swap count — a player who accepted the baseline and never swapped still has a row,
     * so their second empty batch is rejected like anyone else's.
     *
     * <p>Runs before team validation: an empty batch has no codes to validate, and would otherwise
     * fall through to a vacuous expectedOrder comparison that passes whenever the client's order
     * happens to match.
     */
    private Either<FinalTableError, Ctx> requireSomethingToSave(Ctx ctx, SaveFinalTableCommand command) {
        return command.isEmptyBatch() && ctx.existed()
                ? Either.left(new FinalTableError.NothingToSave())
                : Either.right(ctx);
    }

    private Either<FinalTableError, FinalTablePrediction> replay(Ctx ctx, SaveFinalTableCommand command) {
        List<TeamRank> rankings =
                new ArrayList<>(TeamRank.inPositionOrder(ctx.prediction().getRankings()));
        Set<String> validCodes = finalTableSupport.validCodes(ctx.season());
        Instant now = clock.instant();
        List<SwapChange> applied = new ArrayList<>();

        for (SwapPair pair : command.safeSwaps()) {
            var resolved = resolvePair(pair, validCodes, rankings);
            if (resolved.isLeft()) {
                return Either.left(resolved.getLeft());
            }
            var teams = resolved.get();
            applied.add(swapHelper.applySwap(rankings, teams.teamA(), teams.teamB(), now));
        }

        List<TeamRank> ordered = TeamRank.inPositionOrder(rankings);
        List<String> actualOrder = ordered.stream().map(TeamRank::getCode).toList();
        if (!actualOrder.equals(command.safeExpectedOrder())) {
            return Either.left(new FinalTableError.OutOfSync(command.safeExpectedOrder(), actualOrder));
        }

        FinalTablePrediction prediction = ctx.prediction();
        prediction.setRankings(ordered);
        // addSwap is the only writer of swaps/swapCount/settledAt, so an empty batch cannot move the
        // settle time — which is what keeps the tiebreak honest across a nine-month editing window.
        applied.forEach(change -> prediction.addSwap(change, now));

        return Either.right(predictionRepo.save(prediction));
    }

    private record TeamPair(TeamRank teamA, TeamRank teamB) {}

    private Either<FinalTableError, TeamPair> resolvePair(
            SwapPair pair, Set<String> validCodes, List<TeamRank> rankings) {
        String codeA = normalise(pair.teamA());
        String codeB = normalise(pair.teamB());

        if (!validCodes.contains(codeA)) return Either.left(new FinalTableError.InvalidTeamCode(codeA));
        if (!validCodes.contains(codeB)) return Either.left(new FinalTableError.InvalidTeamCode(codeB));
        if (codeA.equals(codeB)) return Either.left(new FinalTableError.InvalidTeamCode(codeA));

        TeamRank teamA = find(rankings, codeA);
        TeamRank teamB = find(rankings, codeB);
        if (teamA == null || teamB == null) return Either.left(new FinalTableError.TeamsNotFound(codeA, codeB));

        return Either.right(new TeamPair(teamA, teamB));
    }

    private static String normalise(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    private static TeamRank find(List<TeamRank> rankings, String code) {
        return rankings.stream()
                .filter(t -> t.getCode().equals(code))
                .findFirst()
                .orElse(null);
    }
}
