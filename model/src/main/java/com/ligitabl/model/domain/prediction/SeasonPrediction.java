package com.ligitabl.model.domain.prediction;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.ligitabl.model.domain.AbstractModel;
import com.ligitabl.model.domain.league.RoundStatus;
import com.ligitabl.model.domain.league.TeamRank;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder(toBuilder = true)
public class SeasonPrediction extends AbstractModel<UUID> {

    public static final Duration SWAP_COOLDOWN = Duration.ofHours(24);

    @NotNull
    private UUID userId;

    @NotNull
    private UUID seasonId;

    /**
     * Non-null only when this user pre-registered during the off-season window: the rankings
     * snapshot (after their one-time 0-5 swaps) at the moment of pre-registration. Null means
     * this prediction was created through the normal in-season join flow.
     */
    private List<TeamRank> initialRankings;

    @NotNull
    private List<TeamRank> currentRankings;

    @Builder.Default
    private List<RoundSwap> swaps = new ArrayList<>();

    private Instant lastSwapAt;

    private int openingCommittedRound;

    private int atRoundNumber;

    // Populated by the database (defaults/triggers)
    private OffsetDateTime createDate;
    private OffsetDateTime updateDate;

    /**
     * Adds a swap to the history for a specific round.
     */
    public void addSwap(int roundNumber, SwapChange change) {
        // Find or create RoundSwap for this round
        RoundSwap roundSwap = swaps.stream()
                .filter(rs -> rs.getRound() == roundNumber)
                .findFirst()
                .orElseGet(() -> {
                    RoundSwap newRoundSwap = new RoundSwap(roundNumber, new ArrayList<>());
                    swaps.add(newRoundSwap);
                    return newRoundSwap;
                });

        // Add the change
        roundSwap.getChanges().add(change);
    }

    /**
     * True for the one-time pre-season registration row (created via the off-season 0-5 swap
     * window, before predictions officially open) — mirrors the atRoundNumber==0 convention used
     * by CreatePredictionUseCase's pre-season registration flow.
     */
    public boolean isPreSeasonRegistration() {
        return atRoundNumber == 0;
    }

    /** The round a swap made now applies to: the next one if this user joined it while the current was locked. */
    public int swapTargetRound(int currentRound) {
        return atRoundNumber == currentRound + 1 ? atRoundNumber : currentRound;
    }

    /** Whether a single swap may be made in {@code round}: the first is free, then the opening window, then the cooldown. */
    public SwapEligibility swapEligibility(int round, RoundStatus status, Instant now) {
        if (status != RoundStatus.OPEN) {
            return new SwapEligibility.RoundNotOpen(status);
        }
        if (isPreSeasonRegistration()) {
            return new SwapEligibility.PreSeasonMergeRequired();
        }
        if (lastSwapAt == null) {
            return new SwapEligibility.FirstSwapFree();
        }
        if (isOpeningWindowOpen(round)) {
            return new SwapEligibility.UseOpeningWindowFirst();
        }
        Instant nextSwapAt = lastSwapAt.plus(SWAP_COOLDOWN);
        return now.isBefore(nextSwapAt)
                ? new SwapEligibility.CooldownActive(nextSwapAt)
                : new SwapEligibility.CooldownElapsed();
    }

    /** Whether the opening window may be used in {@code round}. It ignores the cooldown. */
    public SwapEligibility openingSwapEligibility(int round, RoundStatus status) {
        if (status != RoundStatus.OPEN) {
            return new SwapEligibility.RoundNotOpen(status);
        }
        if (isPreSeasonRegistration()) {
            return new SwapEligibility.PreSeasonMergeRequired();
        }
        return isOpeningWindowOpen(round)
                ? new SwapEligibility.OpeningWindowOpen()
                : new SwapEligibility.OpeningAlreadyUsed();
    }

    /** One swap, under {@link #swapEligibility}. */
    public SwapChange swap(TeamSwap teams, int round, RoundStatus status, Instant now) {
        requireAllowed(swapEligibility(round, status, now));
        SwapChange change = exchange(teams, now);
        addSwap(round, change);
        lastSwapAt = now;
        atRoundNumber = round;
        return change;
    }

    /** The opening window's batch, applied in order. Commits the window for {@code round}. */
    public List<SwapChange> applyOpeningSwaps(List<TeamSwap> batch, int round, RoundStatus status, Instant now) {
        requireAllowed(openingSwapEligibility(round, status));
        List<SwapChange> changes = new ArrayList<>();
        for (TeamSwap teams : batch) {
            SwapChange change = exchange(teams, now);
            addSwap(round, change);
            changes.add(change);
        }
        openingCommittedRound = round;
        lastSwapAt = now;
        atRoundNumber = round;
        return changes;
    }

    /**
     * Turns the pre-season registration into a prediction played from {@code round}. Merging
     * consumes that round's opening window, as joining does. The first swap stays free only if no
     * swap was made before or during the merge.
     */
    public void mergePreSeason(int round, List<TeamRank> rankings, List<SwapChange> changes, Instant now) {
        if (!isPreSeasonRegistration()) {
            throw new IllegalStateException("Prediction " + getId() + " is not a pre-season registration");
        }
        boolean hadSwaps = swaps.stream().anyMatch(s -> !s.getChanges().isEmpty());
        atRoundNumber = round;
        currentRankings = rankings;
        changes.forEach(change -> addSwap(round, change));
        lastSwapAt = hadSwaps || !changes.isEmpty() ? now : null;
        openingCommittedRound = round;
    }

    /**
     * Join and merge stamp openingCommittedRound with the round they start in, so the window
     * opens in every later round. Rows predating that column were backfilled to 0: for those,
     * already being at this round means the window was used.
     */
    private boolean isOpeningWindowOpen(int round) {
        return openingCommittedRound == 0 ? atRoundNumber != round : openingCommittedRound < round;
    }

    private SwapChange exchange(TeamSwap teams, Instant at) {
        List<TeamRank> rankings = new ArrayList<>(currentRankings);
        SwapChange change =
                SwapChange.exchange(rankings, find(rankings, teams.teamACode()), find(rankings, teams.teamBCode()), at);
        currentRankings = rankings;
        return change;
    }

    private static TeamRank find(List<TeamRank> rankings, String code) {
        return rankings.stream()
                .filter(t -> t.getCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Team " + code + " is not in this prediction"));
    }

    private static void requireAllowed(SwapEligibility eligibility) {
        if (!eligibility.allowed()) {
            throw new IllegalStateException("Swap not allowed: " + eligibility);
        }
    }
}
