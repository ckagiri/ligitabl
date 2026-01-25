package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TRoundResult.T_ROUND_RESULT;
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
import com.ligitabl.model.db.tables.records.RoundResultRecord;
import com.ligitabl.model.domain.ResultTeamRank;
import com.ligitabl.model.domain.RoundResult;
import com.ligitabl.model.repo.RoundResultRepo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RoundResultPersistenceAdapter implements RoundResultRepo {
    private final DSLContext dsl;

    private static final RoundResultRecordMapper MAPPER = new RoundResultRecordMapper();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public RoundResult save(RoundResult result) {
        if (result == null) {
            throw new IllegalArgumentException("RoundResult must not be null");
        }

        UUID id = result.getId() == null ? UUID.randomUUID() : result.getId();

        dsl.insertInto(T_ROUND_RESULT)
                .set(T_ROUND_RESULT.PK_ID, id)
                .set(T_ROUND_RESULT.FK_ROUND_SUBMISSION_ID, result.getRoundSubmissionId())
                .set(T_ROUND_RESULT.C_RANKINGS, writeRankings(result.getRankings()))
                .set(T_ROUND_RESULT.C_SCORE, result.getTotalScore())
                .set(T_ROUND_RESULT.C_ZEROES_COUNT, result.getZeroesCount())
                .set(T_ROUND_RESULT.C_SWAP_COUNT, result.getSwapCount())
                .set(T_ROUND_RESULT.C_USER_VIEWED, result.isUserViewed())
                .onConflict(T_ROUND_RESULT.FK_ROUND_SUBMISSION_ID)
                .doUpdate()
                .set(T_ROUND_RESULT.C_RANKINGS, writeRankings(result.getRankings()))
                .set(T_ROUND_RESULT.C_SCORE, result.getTotalScore())
                .set(T_ROUND_RESULT.C_ZEROES_COUNT, result.getZeroesCount())
                .set(T_ROUND_RESULT.C_SWAP_COUNT, result.getSwapCount())
                .set(T_ROUND_RESULT.C_USER_VIEWED, result.isUserViewed())
                .execute();

        return findByRoundSubmissionId(result.getRoundSubmissionId())
                .orElseThrow(() -> new NoSuchElementException("RoundResult not found after save"));
    }

    @Override
    public Optional<RoundResult> findByRoundSubmissionId(UUID submissionId) {
        var record = dsl.selectFrom(T_ROUND_RESULT)
                .where(T_ROUND_RESULT.FK_ROUND_SUBMISSION_ID.eq(submissionId))
                .fetchOne();

        return Optional.ofNullable(MAPPER.map(record));
    }

    @Override
    public List<RoundResult> findBySeasonAndRoundPositionRange(UUID seasonId, int fromRound, int toRound) {
        List<RoundResultRecord> records = dsl.select(T_ROUND_RESULT.fields())
                .from(T_ROUND_RESULT)
                .join(T_ROUND_SUBMISSION)
                .on(T_ROUND_RESULT.FK_ROUND_SUBMISSION_ID.eq(T_ROUND_SUBMISSION.PK_ID))
                .where(T_ROUND_SUBMISSION
                        .FK_SEASON_ID
                        .eq(seasonId)
                        .and(T_ROUND_SUBMISSION.C_ROUND_POSITION.between(fromRound, toRound)))
                .fetchInto(RoundResultRecord.class);

        return records.stream().map(MAPPER::map).toList();
    }

    private static List<ResultTeamRank> readRankings(JSONB jsonb) {
        if (jsonb == null) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(jsonb.data(), new TypeReference<List<ResultTeamRank>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialize round result rankings JSON", e);
        }
    }

    private static JSONB writeRankings(List<ResultTeamRank> rankings) {
        try {
            String json = OBJECT_MAPPER.writeValueAsString(rankings == null ? List.of() : rankings);
            return JSONB.valueOf(json);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize round result rankings JSON", e);
        }
    }

    private static class RoundResultRecordMapper implements RecordMapper<RoundResultRecord, RoundResult> {
        @Override
        public RoundResult map(RoundResultRecord record) {
            if (record == null) {
                return null;
            }

            return RoundResult.builder()
                    .id(record.getId())
                    .roundSubmissionId(record.getRoundSubmissionId())
                    .rankings(readRankings(record.getRankings()))
                    .totalScore(record.getScore())
                    .zeroesCount(record.getZeroesCount())
                    .swapCount(record.getSwapCount())
                    .userViewed(Boolean.TRUE.equals(record.getUserViewed()))
                    .build();
        }
    }
}
