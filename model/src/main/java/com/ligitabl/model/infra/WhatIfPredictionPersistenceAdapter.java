package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TWhatIfPrediction.T_WHAT_IF_PREDICTION;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.RecordMapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.model.db.tables.records.WhatIfPredictionRecord;
import com.ligitabl.model.domain.WhatIfPrediction;
import com.ligitabl.model.domain.WhatIfScore;
import com.ligitabl.model.repo.WhatIfPredictionRepo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class WhatIfPredictionPersistenceAdapter implements WhatIfPredictionRepo {
    private final DSLContext dsl;

    private static final WhatIfPredictionRecordMapper MAPPER = new WhatIfPredictionRecordMapper();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public WhatIfPrediction save(WhatIfPrediction prediction) {
        if (prediction == null) {
            throw new IllegalArgumentException("WhatIfPrediction must not be null");
        }

        if (prediction.getId() == null) {
            UUID id = UUID.randomUUID();

            int inserted = dsl.insertInto(T_WHAT_IF_PREDICTION)
                    .set(T_WHAT_IF_PREDICTION.PK_ID, id)
                    .set(T_WHAT_IF_PREDICTION.FK_USER_ID, prediction.getUserId())
                    .set(T_WHAT_IF_PREDICTION.FK_ROUND_ID, prediction.getRoundId())
                    .set(T_WHAT_IF_PREDICTION.C_SCORES, writeScores(prediction.getScores()))
                    .onConflict(T_WHAT_IF_PREDICTION.FK_USER_ID, T_WHAT_IF_PREDICTION.FK_ROUND_ID)
                    .doNothing()
                    .execute();

            if (inserted == 0) {
                WhatIfPrediction existing = findByUserAndRound(prediction.getUserId(), prediction.getRoundId())
                        .orElseThrow(
                                () -> new NoSuchElementException("WhatIfPrediction exists but could not be reloaded"));
                prediction.setId(existing.getId());
                return update(prediction);
            }

            return findById(id).orElseThrow(() -> new NoSuchElementException("Failed to create what-if prediction"));
        }

        return update(prediction);
    }

    private WhatIfPrediction update(WhatIfPrediction prediction) {
        int updated = dsl.update(T_WHAT_IF_PREDICTION)
                .set(T_WHAT_IF_PREDICTION.FK_USER_ID, prediction.getUserId())
                .set(T_WHAT_IF_PREDICTION.FK_ROUND_ID, prediction.getRoundId())
                .set(T_WHAT_IF_PREDICTION.C_SCORES, writeScores(prediction.getScores()))
                .set(T_WHAT_IF_PREDICTION.C_UPDATED_AT, OffsetDateTime.now())
                .where(T_WHAT_IF_PREDICTION.PK_ID.eq(prediction.getId()))
                .execute();

        if (updated == 0) {
            throw new NoSuchElementException(
                    String.format("WhatIfPrediction with id %s not found", prediction.getId()));
        }

        return findById(prediction.getId())
                .orElseThrow(() -> new NoSuchElementException("WhatIfPrediction not found after save"));
    }

    private Optional<WhatIfPrediction> findById(UUID id) {
        var record = dsl.selectFrom(T_WHAT_IF_PREDICTION)
                .where(T_WHAT_IF_PREDICTION.PK_ID.eq(id))
                .fetchOne();

        return Optional.ofNullable(MAPPER.map(record));
    }

    @Override
    public Optional<WhatIfPrediction> findByUserAndRound(UUID userId, UUID roundId) {
        var record = dsl.selectFrom(T_WHAT_IF_PREDICTION)
                .where(T_WHAT_IF_PREDICTION.FK_USER_ID.eq(userId).and(T_WHAT_IF_PREDICTION.FK_ROUND_ID.eq(roundId)))
                .fetchOne();

        return Optional.ofNullable(MAPPER.map(record));
    }

    @Override
    public void deleteByUserId(UUID userId) {
        dsl.deleteFrom(T_WHAT_IF_PREDICTION)
                .where(T_WHAT_IF_PREDICTION.FK_USER_ID.eq(userId))
                .execute();
    }

    private static List<WhatIfScore> readScores(JSONB jsonb) {
        if (jsonb == null) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(jsonb.data(), new TypeReference<List<WhatIfScore>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialize what-if prediction scores JSON", e);
        }
    }

    private static JSONB writeScores(List<WhatIfScore> scores) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(scores == null ? List.of() : scores);
            return JSONB.valueOf(json);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize what-if prediction scores JSON", e);
        }
    }

    private static class WhatIfPredictionRecordMapper
            implements RecordMapper<WhatIfPredictionRecord, WhatIfPrediction> {
        @Override
        public WhatIfPrediction map(WhatIfPredictionRecord record) {
            if (record == null) {
                return null;
            }

            return WhatIfPrediction.builder()
                    .id(record.getId())
                    .userId(record.getUserId())
                    .roundId(record.getRoundId())
                    .scores(readScores(record.getScores()))
                    .build();
        }
    }
}
