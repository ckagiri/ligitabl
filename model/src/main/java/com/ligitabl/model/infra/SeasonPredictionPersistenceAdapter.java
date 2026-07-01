package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TSeasonPrediction.T_SEASON_PREDICTION;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.JSONB;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.model.db.tables.records.SeasonPredictionRecord;
import com.ligitabl.model.domain.RoundSwap;
import com.ligitabl.model.domain.SeasonPrediction;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.SeasonPredictionRepo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SeasonPredictionPersistenceAdapter implements SeasonPredictionRepo {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private static final TypeReference<List<TeamRank>> TEAM_RANK_LIST = new TypeReference<>() {};
    private static final TypeReference<List<RoundSwap>> ROUND_SWAP_LIST = new TypeReference<>() {};

    private final DSLContext dsl;

    @Override
    public Optional<SeasonPrediction> findByUserAndSeason(UUID userId, UUID seasonId) {
        var record = dsl.selectFrom(T_SEASON_PREDICTION)
                .where(T_SEASON_PREDICTION.FK_USER_ID.eq(userId).and(T_SEASON_PREDICTION.FK_SEASON_ID.eq(seasonId)))
                .fetchOne();

        return Optional.ofNullable(map(record));
    }

    @Override
    public boolean existsByUserAndSeason(UUID userId, UUID seasonId) {
        return dsl.fetchExists(dsl.selectOne()
                .from(T_SEASON_PREDICTION)
                .where(T_SEASON_PREDICTION.FK_USER_ID.eq(userId).and(T_SEASON_PREDICTION.FK_SEASON_ID.eq(seasonId))));
    }

    @Override
    public List<SeasonPrediction> findBySeasonAndAtRoundNumberLessThanEqual(UUID seasonId, int roundNumber) {
        return dsl.selectFrom(T_SEASON_PREDICTION)
                .where(T_SEASON_PREDICTION
                        .FK_SEASON_ID
                        .eq(seasonId)
                        .and(T_SEASON_PREDICTION.C_AT_ROUND_NUMBER.le(roundNumber)))
                .fetch()
                .map(this::map);
    }

    @Override
    public SeasonPrediction save(SeasonPrediction prediction) {
        if (prediction.getId() != null) {
            SeasonPredictionRecord existing = dsl.selectFrom(T_SEASON_PREDICTION)
                    .where(T_SEASON_PREDICTION.PK_ID.eq(prediction.getId()))
                    .fetchOne();
            if (existing != null) {
                copyModelToRecord(prediction, existing);
                existing.store();
                existing.refresh();
                return map(existing);
            }
        }
        return create(prediction);
    }

    private SeasonPrediction create(SeasonPrediction prediction) {
        UUID id = prediction.getId() != null ? prediction.getId() : UUID.randomUUID();
        SeasonPredictionRecord rec = dsl.newRecord(T_SEASON_PREDICTION);
        rec.setId(id);
        copyModelToRecord(prediction, rec);
        rec.store();
        rec.refresh();
        return map(rec);
    }

    private SeasonPrediction map(SeasonPredictionRecord record) {
        if (record == null) {
            return null;
        }

        return SeasonPrediction.builder()
                .id(record.getId())
                .userId(record.getUserId())
                .seasonId(record.getSeasonId())
                .initialRankings(readJson(record.getInitialRankings(), TEAM_RANK_LIST, null))
                .currentRankings(readJson(record.getCurrentRankings(), TEAM_RANK_LIST, List.of()))
                .swaps(readJson(record.getSwaps(), ROUND_SWAP_LIST, List.of()))
                .lastSwapAt(toInstant(record.getLastSwapAt()))
                .openingCommittedRound(
                        record.getOpeningCommittedRound() != null ? record.getOpeningCommittedRound() : 0)
                .atRoundNumber(record.getAtRoundNumber())
                .createDate(record.getCreateDate())
                .updateDate(record.getUpdateDate())
                .build();
    }

    private static void copyModelToRecord(SeasonPrediction model, SeasonPredictionRecord rec) {
        if (model == null || rec == null) return;
        rec.setUserId(model.getUserId());
        rec.setSeasonId(model.getSeasonId());
        rec.setInitialRankings(writeJsonNullable(model.getInitialRankings()));
        rec.setCurrentRankings(writeJson(model.getCurrentRankings()));
        rec.setSwaps(writeJson(model.getSwaps()));
        rec.setLastSwapAt(toOffsetDateTime(model.getLastSwapAt()));
        rec.setOpeningCommittedRound(model.getOpeningCommittedRound());
        rec.setAtRoundNumber(model.getAtRoundNumber());
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
