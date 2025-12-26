package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TEntry.T_ENTRY;

import java.nio.charset.StandardCharsets;
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
                .onConflict(T_ENTRY.FK_USER_ID, T_ENTRY.FK_CONTEST_ID)
                .doNothing()
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

    private Entry map(EntryRecord record) {
        if (record == null) {
            return null;
        }

        return Entry.builder()
                .id(syntheticId(record.getUserId(), record.getContestId()))
                .userId(record.getUserId())
                .contestId(record.getContestId())
                .joinedAt(null)
                .build();
    }

    private static UUID syntheticId(UUID userId, UUID contestId) {
        return UUID.nameUUIDFromBytes(("entry:" + userId + ":" + contestId).getBytes(StandardCharsets.UTF_8));
    }
}
