package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TContest.T_CONTEST;
import static com.ligitabl.model.db.tables.TEntry.T_ENTRY;
import static com.ligitabl.model.db.tables.TRoundSubmission.T_ROUND_SUBMISSION;
import static org.jooq.impl.DSL.greatest;
import static org.jooq.impl.DSL.inline;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;

import com.ligitabl.model.db.tables.records.EntryRecord;
import com.ligitabl.model.domain.Entry;
import com.ligitabl.model.repo.EntryRepo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class EntryPersistenceAdapter implements EntryRepo {

    private final DSLContext dsl;

    @Override
    public Entry save(Entry entry) {
        if (entry.getUserId() == null) {
            throw new IllegalArgumentException("Entry.userId must not be null");
        }
        if (entry.getContestId() == null) {
            throw new IllegalArgumentException("Entry.contestId must not be null");
        }

        dsl.insertInto(T_ENTRY)
                .set(T_ENTRY.FK_USER_ID, entry.getUserId())
                .set(T_ENTRY.FK_CONTEST_ID, entry.getContestId())
                .set(T_ENTRY.C_JOINED_AT_ROUND, entry.getJoinedAtRound())
                .onConflict(T_ENTRY.FK_USER_ID, T_ENTRY.FK_CONTEST_ID)
                .doUpdate()
                .set(T_ENTRY.C_JOINED_AT_ROUND, entry.getJoinedAtRound())
                .set(T_ENTRY.C_REMOVED_AT, (OffsetDateTime) null)
                .set(T_ENTRY.C_REMOVED_AT_ROUND, (Integer) null)
                .execute();

        if (entry.getId() == null) {
            entry.setId(syntheticId(entry.getUserId(), entry.getContestId()));
        }

        return entry;
    }

    @Override
    public Optional<Entry> findByUserAndContest(UUID userId, UUID contestId) {
        var record = dsl.selectFrom(T_ENTRY)
                .where(T_ENTRY.FK_USER_ID.eq(userId).and(T_ENTRY.FK_CONTEST_ID.eq(contestId)))
                .fetchOne();

        return Optional.ofNullable(map(record));
    }

    @Override
    public List<Entry> findByContestId(UUID contestId) {
        return dsl.selectFrom(T_ENTRY)
                .where(T_ENTRY.FK_CONTEST_ID.eq(contestId))
                .fetch()
                .map(this::map);
    }

    @Override
    public List<Entry> findByUserId(UUID userId) {
        return dsl.selectFrom(T_ENTRY)
                .where(T_ENTRY.FK_USER_ID.eq(userId))
                .fetch()
                .map(this::map);
    }

    @Override
    public int countActiveByContestId(UUID contestId) {
        return dsl.fetchCount(
                dsl.selectOne()
                        .from(T_ENTRY)
                        .where(T_ENTRY.FK_CONTEST_ID.eq(contestId))
                        .and(T_ENTRY.C_REMOVED_AT.isNull()));
    }

    @Override
    public void softRemove(UUID userId, UUID contestId, int removedAtRound) {
        dsl.update(T_ENTRY)
                .set(T_ENTRY.C_REMOVED_AT, OffsetDateTime.now())
                .set(T_ENTRY.C_REMOVED_AT_ROUND, removedAtRound)
                .where(T_ENTRY.FK_USER_ID.eq(userId))
                .and(T_ENTRY.FK_CONTEST_ID.eq(contestId))
                .execute();
    }

    @Override
    public boolean hasAnyScore(UUID userId, UUID contestId) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(T_ROUND_SUBMISSION)
                        .join(T_ENTRY)
                        .on(T_ENTRY.FK_USER_ID.eq(T_ROUND_SUBMISSION.FK_USER_ID))
                        .join(T_CONTEST)
                        .on(T_CONTEST.PK_ID.eq(T_ENTRY.FK_CONTEST_ID)
                                .and(T_CONTEST.FK_SEASON_ID.eq(T_ROUND_SUBMISSION.FK_SEASON_ID)))
                        .where(T_ROUND_SUBMISSION.FK_USER_ID.eq(userId))
                        .and(T_ENTRY.FK_CONTEST_ID.eq(contestId))
                        .and(T_ROUND_SUBMISSION.C_ROUND_POSITION.ge(
                                greatest(T_ENTRY.C_JOINED_AT_ROUND, T_CONTEST.C_FROM_ROUND_POSITION)))
                        .and(T_ROUND_SUBMISSION.C_ROUND_POSITION.le(T_CONTEST.C_TO_ROUND_POSITION)));
    }

    private Entry map(EntryRecord record) {
        if (record == null) {
            return null;
        }

        var removedAt = record.getRemovedAt();

        return Entry.builder()
                .id(syntheticId(record.getUserId(), record.getContestId()))
                .userId(record.getUserId())
                .contestId(record.getContestId())
                .joinedAt(null)
                .joinedAtRound(record.getJoinedAtRound() != null ? record.getJoinedAtRound() : 0)
                .removedAt(removedAt != null ? removedAt.toInstant() : null)
                .removedAtRound(record.getRemovedAtRound())
                .build();
    }

    private static UUID syntheticId(UUID userId, UUID contestId) {
        return UUID.nameUUIDFromBytes(("entry:" + userId + ":" + contestId).getBytes(StandardCharsets.UTF_8));
    }
}
