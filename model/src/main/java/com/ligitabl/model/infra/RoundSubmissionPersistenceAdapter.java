package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TRoundSubmission.T_ROUND_SUBMISSION;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.RecordMapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.model.db.tables.records.RoundSubmissionRecord;
import com.ligitabl.model.domain.RoundSubmission;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.RoundSubmissionRepo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RoundSubmissionPersistenceAdapter implements RoundSubmissionRepo {
    private final DSLContext dsl;

    private static final RoundSubmissionRecordMapper MAPPER = new RoundSubmissionRecordMapper();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public RoundSubmission save(RoundSubmission submission) {
        if (submission == null) {
            throw new IllegalArgumentException("RoundSubmission must not be null");
        }

        if (submission.getId() == null) {
            UUID id = UUID.randomUUID();

            int inserted = dsl.insertInto(T_ROUND_SUBMISSION)
                    .set(T_ROUND_SUBMISSION.PK_ID, id)
                    .set(T_ROUND_SUBMISSION.FK_USER_ID, submission.getUserId())
                    .set(T_ROUND_SUBMISSION.FK_SEASON_ID, submission.getSeasonId())
                    .set(T_ROUND_SUBMISSION.C_ROUND_POSITION, submission.getRoundPosition())
                    .set(T_ROUND_SUBMISSION.C_RANKINGS, writeRankings(submission.getRankings()))
                    .set(T_ROUND_SUBMISSION.FK_SEASON_PREDICTION_ID, submission.getSeasonPredictionId())
                    .onConflict(
                            T_ROUND_SUBMISSION.FK_USER_ID,
                            T_ROUND_SUBMISSION.FK_SEASON_ID,
                            T_ROUND_SUBMISSION.C_ROUND_POSITION)
                    .doNothing()
                    .execute();

            if (inserted == 0) {
                return findByUserAndSeasonAndRound(
                                submission.getUserId(), submission.getSeasonId(), submission.getRoundPosition())
                        .orElseThrow(
                                () -> new NoSuchElementException("RoundSubmission exists but could not be reloaded"));
            }

            return findById(id).orElseThrow(() -> new NoSuchElementException("Failed to create round submission"));
        }

        int updated = dsl.update(T_ROUND_SUBMISSION)
                .set(T_ROUND_SUBMISSION.FK_USER_ID, submission.getUserId())
                .set(T_ROUND_SUBMISSION.FK_SEASON_ID, submission.getSeasonId())
                .set(T_ROUND_SUBMISSION.C_ROUND_POSITION, submission.getRoundPosition())
                .set(T_ROUND_SUBMISSION.C_RANKINGS, writeRankings(submission.getRankings()))
                .set(T_ROUND_SUBMISSION.FK_SEASON_PREDICTION_ID, submission.getSeasonPredictionId())
                .where(T_ROUND_SUBMISSION.PK_ID.eq(submission.getId()))
                .execute();

        if (updated == 0) {
            throw new NoSuchElementException(String.format("RoundSubmission with id %s not found", submission.getId()));
        }

        return findById(submission.getId())
                .orElseThrow(() -> new NoSuchElementException("RoundSubmission not found after save"));
    }

    @Override
    public Optional<RoundSubmission> findById(UUID id) {
        var record = dsl.selectFrom(T_ROUND_SUBMISSION)
                .where(T_ROUND_SUBMISSION.PK_ID.eq(id))
                .fetchOne();

        return Optional.ofNullable(MAPPER.map(record));
    }

    @Override
    public Optional<RoundSubmission> findByUserAndSeasonAndRound(UUID userId, UUID seasonId, int roundPosition) {
        var record = dsl.selectFrom(T_ROUND_SUBMISSION)
                .where(T_ROUND_SUBMISSION
                        .FK_USER_ID
                        .eq(userId)
                        .and(T_ROUND_SUBMISSION.FK_SEASON_ID.eq(seasonId))
                        .and(T_ROUND_SUBMISSION.C_ROUND_POSITION.eq(roundPosition)))
                .fetchOne();

        return Optional.ofNullable(MAPPER.map(record));
    }

    @Override
    public List<RoundSubmission> findBySeasonAndRound(UUID seasonId, int roundPosition) {
        return dsl.selectFrom(T_ROUND_SUBMISSION)
                .where(T_ROUND_SUBMISSION
                        .FK_SEASON_ID
                        .eq(seasonId)
                        .and(T_ROUND_SUBMISSION.C_ROUND_POSITION.eq(roundPosition)))
                .fetch()
                .map(MAPPER::map);
    }

    @Override
    public boolean existsByRound(UUID seasonId, int roundPosition) {
        if (seasonId == null) {
            throw new IllegalArgumentException("seasonId must not be null");
        }

        return dsl.fetchExists(dsl.selectOne()
                .from(T_ROUND_SUBMISSION)
                .where(T_ROUND_SUBMISSION
                        .FK_SEASON_ID
                        .eq(seasonId)
                        .and(T_ROUND_SUBMISSION.C_ROUND_POSITION.eq(roundPosition))));
    }

    @Override
    public boolean existsBySeasonId(UUID seasonId) {
        if (seasonId == null) {
            throw new IllegalArgumentException("seasonId must not be null");
        }

        return dsl.fetchExists(
                dsl.selectOne().from(T_ROUND_SUBMISSION).where(T_ROUND_SUBMISSION.FK_SEASON_ID.eq(seasonId)));
    }

    private static List<TeamRank> readRankings(JSONB jsonb) {
        if (jsonb == null) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(jsonb.data(), new TypeReference<List<TeamRank>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialize round submission rankings JSON", e);
        }
    }

    private static JSONB writeRankings(List<TeamRank> rankings) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(rankings == null ? List.of() : rankings);
            return JSONB.valueOf(json);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize round submission rankings JSON", e);
        }
    }

    private static class RoundSubmissionRecordMapper implements RecordMapper<RoundSubmissionRecord, RoundSubmission> {
        @Override
        public RoundSubmission map(RoundSubmissionRecord record) {
            if (record == null) {
                return null;
            }

            return RoundSubmission.builder()
                    .id(record.getId())
                    .userId(record.getUserId())
                    .seasonId(record.getSeasonId())
                    .roundPosition(record.getRoundPosition())
                    .rankings(readRankings(record.getRankings()))
                    .seasonPredictionId(record.getSeasonPredictionId())
                    .build();
        }
    }
}
