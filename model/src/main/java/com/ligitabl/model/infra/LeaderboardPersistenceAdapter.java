package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TEntry.T_ENTRY;
import static com.ligitabl.model.db.tables.TRound.T_ROUND;
import static com.ligitabl.model.db.tables.TRoundResult.T_ROUND_RESULT;
import static com.ligitabl.model.db.tables.TRoundSubmission.T_ROUND_SUBMISSION;
import static com.ligitabl.model.db.tables.TUser.T_USER;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.jooq.CommonTableExpression;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record7;
import org.jooq.Record8;
import org.jooq.impl.DSL;

import com.ligitabl.model.domain.LeaderboardEntry;
import com.ligitabl.model.domain.LeaderboardResponse;
import com.ligitabl.model.repo.LeaderboardRepo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LeaderboardPersistenceAdapter implements LeaderboardRepo {

    private final DSLContext dsl;

    @Override
    public LeaderboardResponse computeLeaderboard(
            UUID contestId, UUID seasonId, int fromRound, int toRound, UUID userId, int offset, int limit) {
        validateInputs(contestId, seasonId, fromRound, toRound, offset, limit);

        Integer effectiveToRound = resolveEffectiveToRound(seasonId, fromRound, toRound);
        if (effectiveToRound == null) {
            return emptyResponse();
        }

        int totalParticipants = countParticipants(contestId, seasonId, fromRound, effectiveToRound);
        if (totalParticipants == 0) {
            return emptyResponse();
        }

        List<RankingWithPosition> pageRankings =
                fetchPaginatedRankings(contestId, seasonId, fromRound, effectiveToRound, offset, limit);

        UserRankingInfo userInfo = userId != null
                ? fetchUserRanking(contestId, seasonId, fromRound, effectiveToRound, userId, offset, limit)
                : new UserRankingInfo(null, false, 0);

        int previousToRound = effectiveToRound - 1;
        HashMap<UUID, Integer> previousPositions = previousToRound >= fromRound
                ? fetchPreviousPositions(contestId, seasonId, fromRound, previousToRound)
                : new HashMap<>();

        List<LeaderboardEntry> entries = pageRankings.stream()
                .map(ranking -> buildEntry(ranking, previousPositions))
                .toList();

        LeaderboardEntry userEntry =
                userInfo.ranking() != null ? buildEntry(userInfo.ranking(), previousPositions) : null;

        return new LeaderboardResponse(
                entries,
                userEntry,
                userInfo.userInCurrentPage(),
                userInfo.userPageOffset(),
                totalParticipants,
                offset + limit < totalParticipants,
                offset > 0);
    }

    @Override
    public Integer resolveEffectiveToRound(UUID seasonId, int fromRound, int toRound) {
        return dsl.select(DSL.max(T_ROUND.C_POSITION))
                .from(T_ROUND)
                .where(T_ROUND.FK_SEASON_ID.eq(seasonId))
                .and(T_ROUND.C_POSITION.between(fromRound, toRound))
                .and(T_ROUND.C_IS_FINALIZED.isTrue())
                .fetchOne(0, Integer.class);
    }

    private record RankingWithPosition(
            int position,
            UUID userId,
            String publicId,
            String displayName,
            int totalScore,
            int roundScore,
            int maxScore,
            int totalZeroes,
            int totalSwaps) {}

    private record UserRankingInfo(RankingWithPosition ranking, boolean userInCurrentPage, int userPageOffset) {}

    private void validateInputs(UUID contestId, UUID seasonId, int fromRound, int toRound, int offset, int limit) {
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
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (limit > 100) {
            throw new IllegalArgumentException("limit must not exceed 100");
        }
    }

    private LeaderboardResponse emptyResponse() {
        return new LeaderboardResponse(List.of(), null, false, 0, 0, false, false);
    }

    private int countParticipants(UUID contestId, UUID seasonId, int fromRound, int toRound) {
        Integer count = dsl.select(DSL.countDistinct(T_ENTRY.FK_USER_ID))
                .from(T_ENTRY)
                .join(T_ROUND_SUBMISSION)
                .on(T_ROUND_SUBMISSION
                        .FK_USER_ID
                        .eq(T_ENTRY.FK_USER_ID)
                        .and(T_ROUND_SUBMISSION.FK_SEASON_ID.eq(seasonId))
                        .and(T_ROUND_SUBMISSION.C_ROUND_POSITION.between(fromRound, toRound)))
                .join(T_ROUND_RESULT)
                .on(T_ROUND_RESULT.FK_ROUND_SUBMISSION_ID.eq(T_ROUND_SUBMISSION.PK_ID))
                .where(T_ENTRY.FK_CONTEST_ID.eq(contestId))
                .fetchOne(0, Integer.class);

        return count != null ? count : 0;
    }

    private List<RankingWithPosition> fetchPaginatedRankings(
            UUID contestId, UUID seasonId, int fromRound, int toRound, int offset, int limit) {

        Field<Integer> totalScore = DSL.coalesce(DSL.sum(T_ROUND_RESULT.C_SCORE), 0)
                .cast(Integer.class)
                .as("total_score");
        Field<Integer> totalZeroes = DSL.coalesce(DSL.sum(T_ROUND_RESULT.C_ZEROES_COUNT), 0)
                .cast(Integer.class)
                .as("total_zeroes");
        Field<Integer> totalSwaps = DSL.coalesce(DSL.sum(T_ROUND_RESULT.C_SWAP_COUNT), 0)
                .cast(Integer.class)
                .as("total_swaps");
        Field<Integer> maxScore = DSL.coalesce(DSL.max(T_ROUND_RESULT.C_SCORE), 0)
                .cast(Integer.class)
                .as("max_score");
        Field<Integer> roundScore = DSL.coalesce(
                        DSL.sum(DSL.when(T_ROUND_SUBMISSION.C_ROUND_POSITION.eq(toRound), T_ROUND_RESULT.C_SCORE)
                                .otherwise(DSL.inline(0))),
                        DSL.inline(0))
                .cast(Integer.class)
                .as("round_score");

        CommonTableExpression<Record8<UUID, String, String, Integer, Integer, Integer, Integer, Integer>> userStats =
                DSL.name("user_stats")
                        .as(dsl.select(
                                        T_USER.PK_ID,
                                        T_USER.C_PUBLIC_ID,
                                        T_USER.C_DISPLAY_NAME,
                                        totalScore,
                                        roundScore,
                                        maxScore,
                                        totalZeroes,
                                        totalSwaps)
                                .from(T_ENTRY)
                                .join(T_USER)
                                .on(T_USER.PK_ID.eq(T_ENTRY.FK_USER_ID))
                                .join(T_ROUND_SUBMISSION)
                                .on(T_ROUND_SUBMISSION
                                        .FK_USER_ID
                                        .eq(T_ENTRY.FK_USER_ID)
                                        .and(T_ROUND_SUBMISSION.FK_SEASON_ID.eq(seasonId))
                                        .and(T_ROUND_SUBMISSION.C_ROUND_POSITION.between(fromRound, toRound)))
                                .join(T_ROUND_RESULT)
                                .on(T_ROUND_RESULT.FK_ROUND_SUBMISSION_ID.eq(T_ROUND_SUBMISSION.PK_ID))
                                .where(T_ENTRY.FK_CONTEST_ID.eq(contestId))
                                .groupBy(T_USER.PK_ID, T_USER.C_PUBLIC_ID, T_USER.C_DISPLAY_NAME));

        Field<UUID> userId = userStats.field(T_USER.PK_ID);
        Field<String> publicId = userStats.field(T_USER.C_PUBLIC_ID);
        Field<String> displayName = userStats.field(T_USER.C_DISPLAY_NAME);
        Field<Integer> totalScoreField = userStats.field("total_score", Integer.class);
        Field<Integer> roundScoreField = userStats.field("round_score", Integer.class);
        Field<Integer> maxScoreField = userStats.field("max_score", Integer.class);
        Field<Integer> totalZeroesField = userStats.field("total_zeroes", Integer.class);
        Field<Integer> totalSwapsField = userStats.field("total_swaps", Integer.class);

        Field<Integer> position = DSL.rowNumber()
                .over()
                .orderBy(
                        totalScoreField.desc(),
                        totalZeroesField.desc(),
                        totalSwapsField.asc(),
                        maxScoreField.desc(),
                        publicId.asc())
                .as("position");

        return dsl.with(userStats)
                .select(
                        position,
                        userId,
                        publicId,
                        displayName,
                        totalScoreField,
                        roundScoreField,
                        maxScoreField,
                        totalZeroesField,
                        totalSwapsField)
                .from(userStats)
                .orderBy(position)
                .limit(limit)
                .offset(offset)
                .fetch(r -> new RankingWithPosition(
                        r.get(position),
                        r.get(userId),
                        r.get(publicId),
                        r.get(displayName),
                        r.get(totalScoreField),
                        r.get(roundScoreField),
                        r.get(maxScoreField),
                        r.get(totalZeroesField),
                        r.get(totalSwapsField)));
    }

    private UserRankingInfo fetchUserRanking(
            UUID contestId, UUID seasonId, int fromRound, int toRound, UUID userId, int offset, int limit) {
        Field<Integer> totalScore = DSL.coalesce(DSL.sum(T_ROUND_RESULT.C_SCORE), 0)
                .cast(Integer.class)
                .as("total_score");
        Field<Integer> totalZeroes = DSL.coalesce(DSL.sum(T_ROUND_RESULT.C_ZEROES_COUNT), 0)
                .cast(Integer.class)
                .as("total_zeroes");
        Field<Integer> totalSwaps = DSL.coalesce(DSL.sum(T_ROUND_RESULT.C_SWAP_COUNT), 0)
                .cast(Integer.class)
                .as("total_swaps");
        Field<Integer> maxScore = DSL.coalesce(DSL.max(T_ROUND_RESULT.C_SCORE), 0)
                .cast(Integer.class)
                .as("max_score");
        Field<Integer> roundScore = DSL.coalesce(
                        DSL.sum(DSL.when(T_ROUND_SUBMISSION.C_ROUND_POSITION.eq(toRound), T_ROUND_RESULT.C_SCORE)
                                .otherwise(DSL.inline(0))),
                        DSL.inline(0))
                .cast(Integer.class)
                .as("round_score");

        CommonTableExpression<Record8<UUID, String, String, Integer, Integer, Integer, Integer, Integer>> userStats =
                DSL.name("user_stats")
                        .as(dsl.select(
                                        T_USER.PK_ID,
                                        T_USER.C_PUBLIC_ID,
                                        T_USER.C_DISPLAY_NAME,
                                        totalScore,
                                        roundScore,
                                        maxScore,
                                        totalZeroes,
                                        totalSwaps)
                                .from(T_ENTRY)
                                .join(T_USER)
                                .on(T_USER.PK_ID.eq(T_ENTRY.FK_USER_ID))
                                .join(T_ROUND_SUBMISSION)
                                .on(T_ROUND_SUBMISSION
                                        .FK_USER_ID
                                        .eq(T_ENTRY.FK_USER_ID)
                                        .and(T_ROUND_SUBMISSION.FK_SEASON_ID.eq(seasonId))
                                        .and(T_ROUND_SUBMISSION.C_ROUND_POSITION.between(fromRound, toRound)))
                                .join(T_ROUND_RESULT)
                                .on(T_ROUND_RESULT.FK_ROUND_SUBMISSION_ID.eq(T_ROUND_SUBMISSION.PK_ID))
                                .where(T_ENTRY.FK_CONTEST_ID.eq(contestId))
                                .groupBy(T_USER.PK_ID, T_USER.C_PUBLIC_ID, T_USER.C_DISPLAY_NAME));

        Field<UUID> userIdField = userStats.field(T_USER.PK_ID);
        Field<String> publicId = userStats.field(T_USER.C_PUBLIC_ID);
        Field<String> displayName = userStats.field(T_USER.C_DISPLAY_NAME);
        Field<Integer> totalScoreField = userStats.field("total_score", Integer.class);
        Field<Integer> roundScoreField = userStats.field("round_score", Integer.class);
        Field<Integer> maxScoreField = userStats.field("max_score", Integer.class);
        Field<Integer> totalZeroesField = userStats.field("total_zeroes", Integer.class);
        Field<Integer> totalSwapsField = userStats.field("total_swaps", Integer.class);

        Field<Integer> position = DSL.rowNumber()
                .over()
                .orderBy(
                        totalScoreField.desc(),
                        totalZeroesField.desc(),
                        totalSwapsField.asc(),
                        maxScoreField.desc(),
                        publicId.asc())
                .as("position");

        var rankedStats = dsl.select(
                        position.as("position"),
                        userIdField.as("user_id"),
                        publicId.as("public_id"),
                        displayName.as("display_name"),
                        totalScoreField.as("total_score"),
                        roundScoreField.as("round_score"),
                        maxScoreField.as("max_score"),
                        totalZeroesField.as("total_zeroes"),
                        totalSwapsField.as("total_swaps"))
                .from(userStats)
                .asTable("ranked_stats");

        Field<Integer> rankedPosition = rankedStats.field("position", Integer.class);
        Field<UUID> rankedUserId = rankedStats.field("user_id", UUID.class);
        Field<String> rankedPublicId = rankedStats.field("public_id", String.class);
        Field<String> rankedDisplayName = rankedStats.field("display_name", String.class);
        Field<Integer> rankedTotalScore = rankedStats.field("total_score", Integer.class);
        Field<Integer> rankedRoundScore = rankedStats.field("round_score", Integer.class);
        Field<Integer> rankedMaxScore = rankedStats.field("max_score", Integer.class);
        Field<Integer> rankedTotalZeroes = rankedStats.field("total_zeroes", Integer.class);
        Field<Integer> rankedTotalSwaps = rankedStats.field("total_swaps", Integer.class);

        RankingWithPosition ranking = dsl.with(userStats)
                .selectFrom(rankedStats)
                .where(rankedUserId.eq(userId))
                .fetchOne(r -> new RankingWithPosition(
                        r.get(rankedPosition),
                        r.get(rankedUserId),
                        r.get(rankedPublicId),
                        r.get(rankedDisplayName),
                        r.get(rankedTotalScore),
                        r.get(rankedRoundScore),
                        r.get(rankedMaxScore),
                        r.get(rankedTotalZeroes),
                        r.get(rankedTotalSwaps)));

        if (ranking == null) {
            return new UserRankingInfo(null, false, 0);
        }

        boolean userInCurrentPage = ranking.position() > offset && ranking.position() <= offset + limit;
        int userPageOffset = ((ranking.position() - 1) / limit) * limit;

        return new UserRankingInfo(ranking, userInCurrentPage, userPageOffset);
    }

    private HashMap<UUID, Integer> fetchPreviousPositions(UUID contestId, UUID seasonId, int fromRound, int toRound) {
        Field<Integer> totalScore = DSL.coalesce(DSL.sum(T_ROUND_RESULT.C_SCORE), 0)
                .cast(Integer.class)
                .as("total_score");
        Field<Integer> totalZeroes = DSL.coalesce(DSL.sum(T_ROUND_RESULT.C_ZEROES_COUNT), 0)
                .cast(Integer.class)
                .as("total_zeroes");
        Field<Integer> totalSwaps = DSL.coalesce(DSL.sum(T_ROUND_RESULT.C_SWAP_COUNT), 0)
                .cast(Integer.class)
                .as("total_swaps");
        Field<Integer> maxScore = DSL.coalesce(DSL.max(T_ROUND_RESULT.C_SCORE), 0)
                .cast(Integer.class)
                .as("max_score");

        CommonTableExpression<Record7<UUID, String, String, Integer, Integer, Integer, Integer>> userStats = DSL.name(
                        "user_stats")
                .as(dsl.select(
                                T_USER.PK_ID,
                                T_USER.C_PUBLIC_ID,
                                T_USER.C_DISPLAY_NAME,
                                totalScore,
                                maxScore,
                                totalZeroes,
                                totalSwaps)
                        .from(T_ENTRY)
                        .join(T_USER)
                        .on(T_USER.PK_ID.eq(T_ENTRY.FK_USER_ID))
                        .join(T_ROUND_SUBMISSION)
                        .on(T_ROUND_SUBMISSION
                                .FK_USER_ID
                                .eq(T_ENTRY.FK_USER_ID)
                                .and(T_ROUND_SUBMISSION.FK_SEASON_ID.eq(seasonId))
                                .and(T_ROUND_SUBMISSION.C_ROUND_POSITION.between(fromRound, toRound)))
                        .join(T_ROUND_RESULT)
                        .on(T_ROUND_RESULT.FK_ROUND_SUBMISSION_ID.eq(T_ROUND_SUBMISSION.PK_ID))
                        .where(T_ENTRY.FK_CONTEST_ID.eq(contestId))
                        .groupBy(T_USER.PK_ID, T_USER.C_PUBLIC_ID, T_USER.C_DISPLAY_NAME));

        Field<UUID> userIdField = userStats.field(T_USER.PK_ID);
        Field<String> publicId = userStats.field(T_USER.C_PUBLIC_ID);
        Field<Integer> totalScoreField = userStats.field("total_score", Integer.class);
        Field<Integer> maxScoreField = userStats.field("max_score", Integer.class);
        Field<Integer> totalZeroesField = userStats.field("total_zeroes", Integer.class);
        Field<Integer> totalSwapsField = userStats.field("total_swaps", Integer.class);

        Field<Integer> position = DSL.rowNumber()
                .over()
                .orderBy(
                        totalScoreField.desc(),
                        totalZeroesField.desc(),
                        totalSwapsField.asc(),
                        maxScoreField.desc(),
                        publicId.asc())
                .as("position");

        HashMap<UUID, Integer> positions = new HashMap<>();
        dsl.with(userStats)
                .select(position, userIdField)
                .from(userStats)
                .orderBy(position)
                .fetch(r -> {
                    positions.put(r.get(userIdField), r.get(position));
                    return null;
                });

        return positions;
    }

    private LeaderboardEntry buildEntry(RankingWithPosition ranking, HashMap<UUID, Integer> previousPositions) {
        Integer previousPosition = previousPositions.get(ranking.userId());
        int movement = previousPosition == null ? 0 : previousPosition - ranking.position();

        return new LeaderboardEntry(
                ranking.position(),
                ranking.publicId(),
                ranking.displayName(),
                ranking.totalScore(),
                ranking.roundScore(),
                ranking.maxScore(),
                ranking.totalZeroes(),
                ranking.totalSwaps(),
                movement);
    }
}
