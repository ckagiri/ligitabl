package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TFinalTablePrediction.T_FINAL_TABLE_PREDICTION;
import static com.ligitabl.model.db.tables.TUser.T_USER;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.SelectField;
import org.jooq.impl.DSL;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.model.db.tables.records.FinalTablePredictionRecord;
import com.ligitabl.model.domain.FinalTableEntrant;
import com.ligitabl.model.domain.FinalTableLeaderboardEntry;
import com.ligitabl.model.domain.FinalTablePrediction;
import com.ligitabl.model.domain.ResultTeamRank;
import com.ligitabl.model.domain.SwapChange;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.FinalTablePredictionRepo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FinalTablePredictionPersistenceAdapter implements FinalTablePredictionRepo {

    /** See {@link JsonMappers}: Instant handling cannot rely on classpath discovery here. */
    private static final ObjectMapper OBJECT_MAPPER = JsonMappers.forJsonb();

    private static final TypeReference<List<TeamRank>> TEAM_RANK_LIST = new TypeReference<>() {};
    private static final TypeReference<List<SwapChange>> SWAP_CHANGE_LIST = new TypeReference<>() {};
    private static final TypeReference<List<ResultTeamRank>> RESULT_TEAM_RANK_LIST = new TypeReference<>() {};

    private final DSLContext dsl;

    @Override
    public Optional<FinalTablePrediction> findByUserAndSeason(UUID userId, UUID seasonId) {
        var record = dsl.selectFrom(T_FINAL_TABLE_PREDICTION)
                .where(T_FINAL_TABLE_PREDICTION
                        .FK_USER_ID
                        .eq(userId)
                        .and(T_FINAL_TABLE_PREDICTION.FK_SEASON_ID.eq(seasonId)))
                .fetchOne();

        return Optional.ofNullable(map(record));
    }

    @Override
    public List<FinalTablePrediction> findBySeason(UUID seasonId) {
        return dsl.selectFrom(T_FINAL_TABLE_PREDICTION)
                .where(T_FINAL_TABLE_PREDICTION.FK_SEASON_ID.eq(seasonId))
                .orderBy(T_FINAL_TABLE_PREDICTION.C_CREATE_DATE.asc())
                .fetch()
                .map(this::map);
    }

    @Override
    public FinalTablePrediction save(FinalTablePrediction prediction) {
        if (prediction.getId() != null) {
            FinalTablePredictionRecord existing = dsl.selectFrom(T_FINAL_TABLE_PREDICTION)
                    .where(T_FINAL_TABLE_PREDICTION.PK_ID.eq(prediction.getId()))
                    .fetchOne();
            if (existing != null) {
                copyModelToRecord(prediction, existing);
                existing.setUpdateDate(OffsetDateTime.now(ZoneOffset.UTC));
                existing.store();
                existing.refresh();
                return map(existing);
            }
        }
        return create(prediction);
    }

    private FinalTablePrediction create(FinalTablePrediction prediction) {
        UUID id = prediction.getId() != null ? prediction.getId() : UUID.randomUUID();
        FinalTablePredictionRecord rec = dsl.newRecord(T_FINAL_TABLE_PREDICTION);
        rec.setId(id);
        copyModelToRecord(prediction, rec);
        rec.store();
        rec.refresh();
        return map(rec);
    }

    @Override
    public int countBySeason(UUID seasonId) {
        return dsl.fetchCount(T_FINAL_TABLE_PREDICTION, T_FINAL_TABLE_PREDICTION.FK_SEASON_ID.eq(seasonId));
    }

    @Override
    public int countScoredBySeason(UUID seasonId) {
        return dsl.fetchCount(
                T_FINAL_TABLE_PREDICTION,
                T_FINAL_TABLE_PREDICTION
                        .FK_SEASON_ID
                        .eq(seasonId)
                        .and(T_FINAL_TABLE_PREDICTION.C_SCORED_AT.isNotNull()));
    }

    @Override
    public int clearResults(UUID seasonId) {
        return dsl.update(T_FINAL_TABLE_PREDICTION)
                .setNull(T_FINAL_TABLE_PREDICTION.C_RESULT_RANKINGS)
                .setNull(T_FINAL_TABLE_PREDICTION.C_BASE_SCORE)
                .setNull(T_FINAL_TABLE_PREDICTION.C_ZEROES_COUNT)
                .setNull(T_FINAL_TABLE_PREDICTION.C_BONUS_POINTS)
                .setNull(T_FINAL_TABLE_PREDICTION.C_CHAMPION_BONUS)
                .setNull(T_FINAL_TABLE_PREDICTION.C_TOTAL_SCORE)
                // Must be nulled too: it is the reveal predicate, so leaving it set would produce
                // a "revealed" page with blank numbers.
                .setNull(T_FINAL_TABLE_PREDICTION.C_SCORED_AT)
                .where(T_FINAL_TABLE_PREDICTION.FK_SEASON_ID.eq(seasonId))
                .execute();
    }

    @Override
    public void deleteByUserId(UUID userId) {
        dsl.deleteFrom(T_FINAL_TABLE_PREDICTION)
                .where(T_FINAL_TABLE_PREDICTION.FK_USER_ID.eq(userId))
                .execute();
    }

    @Override
    public List<FinalTableLeaderboardEntry> leaderboard(UUID seasonId, int offset, int limit) {
        return dsl.select(rankedFields())
                .from(T_FINAL_TABLE_PREDICTION)
                .join(T_USER)
                .on(T_USER.PK_ID.eq(T_FINAL_TABLE_PREDICTION.FK_USER_ID))
                .where(scoredInSeason(seasonId))
                .orderBy(leaderboardOrder())
                .limit(limit)
                .offset(offset)
                .fetch(this::mapEntry);
    }

    @Override
    public Optional<FinalTableLeaderboardEntry> userStanding(UUID seasonId, UUID userId) {
        // The position must come from the whole season's ranking, so rank everyone in a derived
        // table and then filter — filtering first would always yield position 1.
        var ranked = dsl.select(rankedFields())
                .from(T_FINAL_TABLE_PREDICTION)
                .join(T_USER)
                .on(T_USER.PK_ID.eq(T_FINAL_TABLE_PREDICTION.FK_USER_ID))
                .where(scoredInSeason(seasonId))
                .asTable("ranked");

        var record = dsl.select(ranked.fields())
                .from(ranked)
                .where(ranked.field("user_id", UUID.class).eq(userId))
                .fetchOne();

        return Optional.ofNullable(record).map(this::mapEntry);
    }

    @Override
    public List<FinalTableEntrant> entrantsBySeason(UUID seasonId) {
        // Same user join as leaderboard(), minus the scored gate — this one has to answer while the
        // season is still running — and minus every score and ranking column.
        return dsl.select(T_USER.C_PUBLIC_ID, T_USER.C_DISPLAY_NAME)
                .from(T_FINAL_TABLE_PREDICTION)
                .join(T_USER)
                .on(T_USER.PK_ID.eq(T_FINAL_TABLE_PREDICTION.FK_USER_ID))
                .where(T_FINAL_TABLE_PREDICTION.FK_SEASON_ID.eq(seasonId))
                // First to settle, first listed; create date only to keep reloads stable.
                .orderBy(T_FINAL_TABLE_PREDICTION.C_SETTLED_AT.asc(), T_FINAL_TABLE_PREDICTION.C_CREATE_DATE.asc())
                .fetch(r -> new FinalTableEntrant(r.get(T_USER.C_PUBLIC_ID), r.get(T_USER.C_DISPLAY_NAME)));
    }

    private org.jooq.Condition scoredInSeason(UUID seasonId) {
        return T_FINAL_TABLE_PREDICTION.FK_SEASON_ID.eq(seasonId).and(T_FINAL_TABLE_PREDICTION.C_SCORED_AT.isNotNull());
    }

    /**
     * Score, then zeroes, then who settled first — swaps are free and unlimited here, so ordering
     * on them would punish exploration. The trailing create-date key is only for determinism.
     */
    private List<? extends org.jooq.OrderField<?>> leaderboardOrder() {
        return List.of(
                T_FINAL_TABLE_PREDICTION.C_TOTAL_SCORE.desc(),
                T_FINAL_TABLE_PREDICTION.C_ZEROES_COUNT.desc(),
                T_FINAL_TABLE_PREDICTION.C_SETTLED_AT.asc(),
                T_FINAL_TABLE_PREDICTION.C_CREATE_DATE.asc());
    }

    private List<SelectField<?>> rankedFields() {
        Field<Integer> position =
                DSL.rowNumber().over().orderBy(leaderboardOrder()).as("position");

        return List.of(
                position,
                T_FINAL_TABLE_PREDICTION.FK_USER_ID.as("user_id"),
                T_USER.C_PUBLIC_ID,
                T_USER.C_DISPLAY_NAME,
                T_FINAL_TABLE_PREDICTION.C_TOTAL_SCORE,
                T_FINAL_TABLE_PREDICTION.C_BASE_SCORE,
                T_FINAL_TABLE_PREDICTION.C_ZEROES_COUNT,
                T_FINAL_TABLE_PREDICTION.C_BONUS_POINTS,
                T_FINAL_TABLE_PREDICTION.C_SWAP_COUNT,
                T_FINAL_TABLE_PREDICTION.C_SETTLED_AT);
    }

    private FinalTableLeaderboardEntry mapEntry(Record r) {
        return new FinalTableLeaderboardEntry(
                r.get("position", Integer.class),
                r.get(T_USER.C_PUBLIC_ID.getName(), String.class),
                r.get(T_USER.C_DISPLAY_NAME.getName(), String.class),
                intOrZero(r.get(T_FINAL_TABLE_PREDICTION.C_TOTAL_SCORE.getName(), Integer.class)),
                intOrZero(r.get(T_FINAL_TABLE_PREDICTION.C_BASE_SCORE.getName(), Integer.class)),
                intOrZero(r.get(T_FINAL_TABLE_PREDICTION.C_ZEROES_COUNT.getName(), Integer.class)),
                intOrZero(r.get(T_FINAL_TABLE_PREDICTION.C_BONUS_POINTS.getName(), Integer.class)),
                intOrZero(r.get(T_FINAL_TABLE_PREDICTION.C_SWAP_COUNT.getName(), Integer.class)),
                toInstant(r.get(T_FINAL_TABLE_PREDICTION.C_SETTLED_AT.getName(), OffsetDateTime.class)));
    }

    private static int intOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private FinalTablePrediction map(FinalTablePredictionRecord record) {
        if (record == null) {
            return null;
        }

        return FinalTablePrediction.builder()
                .id(record.getId())
                .userId(record.getUserId())
                .seasonId(record.getSeasonId())
                .rankings(readJson(record.getRankings(), TEAM_RANK_LIST, List.of()))
                .swaps(readJson(record.getSwaps(), SWAP_CHANGE_LIST, List.of()))
                .swapCount(record.getSwapCount() != null ? record.getSwapCount() : 0)
                .settledAt(toInstant(record.getSettledAt()))
                .resultRankings(readJson(record.getResultRankings(), RESULT_TEAM_RANK_LIST, null))
                .baseScore(record.getBaseScore())
                .zeroesCount(record.getZeroesCount())
                .bonusPoints(record.getBonusPoints())
                .championBonus(record.getChampionBonus())
                .totalScore(record.getTotalScore())
                .scoredAt(toInstant(record.getScoredAt()))
                .createDate(record.getCreateDate())
                .updateDate(record.getUpdateDate())
                .build();
    }

    private static void copyModelToRecord(FinalTablePrediction model, FinalTablePredictionRecord rec) {
        if (model == null || rec == null) return;
        rec.setUserId(model.getUserId());
        rec.setSeasonId(model.getSeasonId());
        rec.setRankings(writeJson(model.getRankings()));
        rec.setSwaps(writeJson(model.getSwaps()));
        rec.setSwapCount(model.getSwapCount());
        // NOT NULL in the schema: a row that has never swapped settles at its create date.
        if (model.getSettledAt() != null) {
            rec.setSettledAt(toOffsetDateTime(model.getSettledAt()));
        }
        rec.setResultRankings(writeJsonNullable(model.getResultRankings()));
        rec.setBaseScore(model.getBaseScore());
        rec.setZeroesCount(model.getZeroesCount());
        rec.setBonusPoints(model.getBonusPoints());
        rec.setChampionBonus(model.getChampionBonus());
        rec.setTotalScore(model.getTotalScore());
        rec.setScoredAt(toOffsetDateTime(model.getScoredAt()));
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static JSONB writeJson(Object value) {
        try {
            return JSONB.valueOf(OBJECT_MAPPER.writeValueAsString(value == null ? List.of() : value));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize JSON", e);
        }
    }

    /** Unlike writeJson, preserves a Java null as SQL NULL instead of defaulting to an empty JSON array. */
    private static JSONB writeJsonNullable(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return JSONB.valueOf(OBJECT_MAPPER.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize JSON", e);
        }
    }

    private static <T> T readJson(JSONB jsonb, TypeReference<T> typeRef, T defaultValue) {
        if (jsonb == null) {
            return defaultValue;
        }

        try {
            return OBJECT_MAPPER.readValue(jsonb.data(), typeRef);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize JSON", e);
        }
    }
}
