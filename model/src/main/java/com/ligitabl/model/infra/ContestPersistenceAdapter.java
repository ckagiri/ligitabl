package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TContest.T_CONTEST;
import static com.ligitabl.model.db.tables.TEntry.T_ENTRY;
import static org.jooq.impl.DSL.upper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;

import com.ligitabl.model.db.tables.records.ContestRecord;
import com.ligitabl.model.domain.Contest;
import com.ligitabl.model.repo.ContestRepo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ContestPersistenceAdapter implements ContestRepo {

    private final DSLContext dsl;

    @Override
    public Optional<Contest> findById(UUID id) {
        var record = dsl.selectFrom(T_CONTEST).where(T_CONTEST.PK_ID.eq(id)).fetchOne();

        return Optional.ofNullable(map(record));
    }

    @Override
    public Contest save(Contest contest) {
        if (contest.getId() == null) {
            contest.setId(UUID.randomUUID());
        }

        dsl.insertInto(T_CONTEST)
                .set(T_CONTEST.PK_ID, contest.getId())
                .set(T_CONTEST.FK_SEASON_ID, contest.getSeasonId())
                .set(T_CONTEST.C_NAME, contest.getName())
                .set(T_CONTEST.C_IS_PRIVATE, contest.isPrivate())
                .set(T_CONTEST.C_JOIN_CODE, contest.getJoinCode())
                .set(T_CONTEST.C_FROM_ROUND_POSITION, contest.getFromRoundPosition())
                .set(T_CONTEST.C_TO_ROUND_POSITION, contest.getToRoundPosition())
                .set(T_CONTEST.C_MAX_ENTRIES, contest.getMaxEntries())
                .set(T_CONTEST.C_OWNER_ID, contest.getOwnerId())
                .set(T_CONTEST.C_IS_OPEN, contest.isOpen())
                .onConflict(T_CONTEST.PK_ID)
                .doUpdate()
                .set(T_CONTEST.FK_SEASON_ID, contest.getSeasonId())
                .set(T_CONTEST.C_NAME, contest.getName())
                .set(T_CONTEST.C_IS_PRIVATE, contest.isPrivate())
                .set(T_CONTEST.C_JOIN_CODE, contest.getJoinCode())
                .set(T_CONTEST.C_FROM_ROUND_POSITION, contest.getFromRoundPosition())
                .set(T_CONTEST.C_TO_ROUND_POSITION, contest.getToRoundPosition())
                .set(T_CONTEST.C_MAX_ENTRIES, contest.getMaxEntries())
                .set(T_CONTEST.C_OWNER_ID, contest.getOwnerId())
                .set(T_CONTEST.C_IS_OPEN, contest.isOpen())
                .execute();

        return contest;
    }

    @Override
    public Optional<Contest> findMainBySeasonId(UUID seasonId) {
        var record = dsl.selectFrom(T_CONTEST)
                .where(T_CONTEST
                        .FK_SEASON_ID
                        .eq(seasonId)
                        .and(T_CONTEST.C_IS_PRIVATE.eq(false))
                        .and(T_CONTEST.C_FROM_ROUND_POSITION.eq(1)))
                .fetchAny();

        return Optional.ofNullable(map(record));
    }

    @Override
    public boolean existsByUserAndContest(UUID userId, UUID contestId) {
        if (userId == null || contestId == null) {
            throw new IllegalArgumentException("userId and contestId must not be null");
        }

        return dsl.fetchExists(dsl.selectOne()
                .from(T_ENTRY)
                .where(T_ENTRY.FK_USER_ID.eq(userId))
                .and(T_ENTRY.FK_CONTEST_ID.eq(contestId)));
    }

    @Override
    public List<Contest> findPrivateByUserId(UUID userId) {
        return dsl.selectFrom(T_CONTEST)
                .where(T_CONTEST.C_IS_PRIVATE.eq(true))
                .and(T_CONTEST.PK_ID.in(dsl.select(T_ENTRY.FK_CONTEST_ID)
                        .from(T_ENTRY)
                        .where(T_ENTRY.FK_USER_ID.eq(userId))
                        .and(T_ENTRY.C_REMOVED_AT_ROUND.isNull())))
                .fetch()
                .map(ContestPersistenceAdapter::map);
    }

    @Override
    public void delete(UUID contestId) {
        dsl.deleteFrom(T_CONTEST).where(T_CONTEST.PK_ID.eq(contestId)).execute();
    }

    @Override
    public Optional<Contest> findByJoinCode(String joinCode) {
        var record = dsl.selectFrom(T_CONTEST)
                .where(upper(T_CONTEST.C_JOIN_CODE).eq(upper(joinCode)))
                .fetchOne();

        return Optional.ofNullable(map(record));
    }

    private static Contest map(ContestRecord record) {
        if (record == null) {
            return null;
        }

        return Contest.builder()
                .id(record.getId())
                .seasonId(record.getSeasonId())
                .name(record.getName())
                .isPrivate(Boolean.TRUE.equals(record.getIsPrivate()))
                .joinCode(record.getJoinCode())
                .fromRoundPosition(record.getFromRoundPosition())
                .toRoundPosition(record.getToRoundPosition())
                .maxEntries(record.getMaxEntries())
                .ownerId(record.getOwnerId())
                .isOpen(Boolean.TRUE.equals(record.getIsOpen()))
                .build();
    }
}
