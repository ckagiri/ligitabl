package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TEntry.T_ENTRY;
import static com.ligitabl.model.db.tables.TRoundResult.T_ROUND_RESULT;
import static com.ligitabl.model.db.tables.TRoundSubmission.T_ROUND_SUBMISSION;
import static com.ligitabl.model.db.tables.TUser.T_USER;
import static com.ligitabl.model.db.tables.TRound.T_ROUND;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;

import com.ligitabl.model.domain.LeaderboardEntry;
import com.ligitabl.model.repo.LeaderboardRepo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LeaderboardPersistenceAdapter implements LeaderboardRepo {

    private final DSLContext dsl;

    @Override
    public List<LeaderboardEntry> computeLeaderboard(UUID contestId, UUID seasonId, int fromRound, int toRound) {
        if (contestId == null) {
            throw new IllegalArgumentException("contestId must not be null");
        }
        if (seasonId == null) {
            throw new IllegalArgumentException("seasonId must not be null");
        }
        if (fromRound <= 0) {
            throw new IllegalArgumentException("fromRound must be positive");
        }
        if (toRound < fromRound) {
            throw new IllegalArgumentException("toRound must be >= fromRound");
        }

        Integer effectiveToRound = resolveEffectiveToRound(contestId, seasonId, fromRound, toRound);
        if (effectiveToRound == null) {
            return List.of();
        }

        List<RankingData> currentRankings = computeRankings(contestId, seasonId, fromRound, effectiveToRound);

        int previousToRound = effectiveToRound - 1;
        List<RankingData> previousRankings = previousToRound >= fromRound
                ? computeRankings(contestId, seasonId, fromRound, previousToRound)
                : List.of();

        HashMap<UUID, Integer> previousPositions = new HashMap<>();
        for (int i = 0; i < previousRankings.size(); i++) {
            previousPositions.put(previousRankings.get(i).userId(), i + 1);
        }

        ArrayList<LeaderboardEntry> out = new ArrayList<>(currentRankings.size());
        for (int i = 0; i < currentRankings.size(); i++) {
            RankingData ranking = currentRankings.get(i);
            int currentPosition = i + 1;

            Integer previousPosition = previousPositions.get(ranking.userId());
            int movement = previousPosition == null ? 0 : previousPosition - currentPosition;

            out.add(new LeaderboardEntry(
                    currentPosition,
                    ranking.displayName(),
                    ranking.totalScore(),
                    ranking.maxScore(),
                    ranking.totalZeroes(),
                    ranking.totalSwaps(),
                    movement));
        }

        return out;
    }

    private Integer resolveEffectiveToRound(UUID contestId, UUID seasonId, int fromRound, int toRound) {
        return dsl.select(DSL.max(T_ROUND_SUBMISSION.C_ROUND_POSITION))
                .from(T_ENTRY)
                .join(T_ROUND_SUBMISSION)
                .on(T_ROUND_SUBMISSION.FK_USER_ID.eq(T_ENTRY.FK_USER_ID)
                        .and(T_ROUND_SUBMISSION.FK_SEASON_ID.eq(seasonId))
                        .and(T_ROUND_SUBMISSION.C_ROUND_POSITION.between(fromRound, toRound)))
                .join(T_ROUND_RESULT)
                .on(T_ROUND_RESULT.FK_ROUND_SUBMISSION_ID.eq(T_ROUND_SUBMISSION.PK_ID))
                .where(T_ENTRY.FK_CONTEST_ID.eq(contestId))
                .fetchOne(0, Integer.class);
    }

    private record RankingData(
            UUID userId,
            String displayName,
            int totalScore,
            int maxScore,
            int totalZeroes,
            int totalSwaps) {}

    private List<RankingData> computeRankings(UUID contestId, UUID seasonId, int fromRound, int toRound) {
        Field<Integer> totalScore = DSL.coalesce(DSL.sum(T_ROUND_RESULT.C_SCORE), DSL.inline(0))
            .cast(Integer.class)
            .as("total_score");
        Field<Integer> totalZeroes = DSL.coalesce(DSL.sum(T_ROUND_RESULT.C_ZEROES_COUNT), DSL.inline(0))
            .cast(Integer.class)
            .as("total_zeroes");
        Field<Integer> totalSwaps = DSL.coalesce(DSL.sum(T_ROUND_RESULT.C_SWAP_COUNT), DSL.inline(0))
            .cast(Integer.class)
            .as("total_swaps");
        Field<Integer> maxScore = DSL.coalesce(DSL.max(T_ROUND_RESULT.C_SCORE), DSL.inline(0))
            .cast(Integer.class)
            .as("max_score");

        return dsl.select(
                        T_USER.PK_ID,
                        T_USER.C_DISPLAY_NAME,
                        totalScore,
                        maxScore,
                        totalZeroes,
                        totalSwaps)
                .from(T_ENTRY)
                .join(T_USER).on(T_USER.PK_ID.eq(T_ENTRY.FK_USER_ID))
                .join(T_ROUND_SUBMISSION)
                .on(T_ROUND_SUBMISSION.FK_USER_ID.eq(T_ENTRY.FK_USER_ID)
                        .and(T_ROUND_SUBMISSION.FK_SEASON_ID.eq(seasonId))
                        .and(T_ROUND_SUBMISSION.C_ROUND_POSITION.between(fromRound, toRound)))
                .join(T_ROUND_RESULT)
                .on(T_ROUND_RESULT.FK_ROUND_SUBMISSION_ID.eq(T_ROUND_SUBMISSION.PK_ID))
                .where(T_ENTRY.FK_CONTEST_ID.eq(contestId))
                .groupBy(T_USER.PK_ID, T_USER.C_DISPLAY_NAME)
                .orderBy(
                        totalScore.desc(),
                        totalZeroes.desc(),
                        totalSwaps.asc(),
                        maxScore.desc(),
                        DSL.lower(T_USER.C_DISPLAY_NAME).asc())
                .fetch(r -> new RankingData(
                        r.get(T_USER.PK_ID),
                        r.get(T_USER.C_DISPLAY_NAME),
                        r.get(totalScore),
                        r.get(maxScore),
                        r.get(totalZeroes),
                        r.get(totalSwaps)));
    }
}
