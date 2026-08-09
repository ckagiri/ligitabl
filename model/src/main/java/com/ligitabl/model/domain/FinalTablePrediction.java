package com.ligitabl.model.domain;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * A user's one locked prediction of the final table for a season.
 *
 * <p>Deliberately not a flavour of {@link SeasonPrediction}: there are no rounds here, no opening
 * window and no cooldown, so the round-by-round columns would all be meaningless. Editable only
 * while round 1 is OPEN, then frozen until an admin completes the season and it is scored exactly
 * once — which is why the result lives in nullable fields on this object rather than in a
 * submission/result pair.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class FinalTablePrediction extends AbstractModel<UUID> {
    @NotNull
    private UUID userId;

    @NotNull
    private UUID seasonId;

    @NotNull
    private List<TeamRank> rankings;

    /**
     * Flat, unlike {@link SeasonPrediction#getSwaps()} — the main game nests by round because
     * finalization counts swaps per round. Every swap here happens before round 1 locks.
     */
    @Builder.Default
    private List<SwapChange> swaps = new ArrayList<>();

    /** Denormalised {@code swaps.size()}. Kept in step by {@link #addSwap}; never set directly. */
    @Builder.Default
    private int swapCount = 0;

    /**
     * The leaderboard tiebreak: when this user settled on their table. Set to the create date when
     * the row is created — a player who accepts the baseline has settled the moment they entered —
     * and thereafter only ever advanced by {@link #addSwap}. Never recomputed, and never rewound:
     * a player who swapped in August and reopens the page in December keeps their August time.
     */
    private Instant settledAt;

    // Result columns: all null until the row is scored.
    private List<ResultTeamRank> resultRankings;
    private Integer baseScore;
    private Integer zeroesCount;
    private Integer bonusPoints;
    private Integer championBonus;
    private Integer totalScore;
    private Instant scoredAt;

    // Populated by the database (defaults/triggers)
    private OffsetDateTime createDate;
    private OffsetDateTime updateDate;

    /**
     * Whether this row has been scored — and therefore whether its result may be revealed. The
     * view depends on this fact rather than on the season being completed, so a season marked
     * complete before the scoring pass runs (or after it fails) still reads as waiting.
     */
    public boolean isScored() {
        return scoredAt != null;
    }

    /**
     * Appends a swap and advances the settle time, keeping {@code swaps}, {@code swapCount} and
     * {@code settledAt} in step. The only writer of any of the three.
     */
    public void addSwap(SwapChange change, Instant at) {
        if (swaps == null) {
            swaps = new ArrayList<>();
        }
        swaps.add(change);
        swapCount = swaps.size();
        settledAt = at;
    }
}
