package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TContest.T_CONTEST;
import static com.ligitabl.model.db.tables.TEntry.T_ENTRY;
import static com.ligitabl.model.db.tables.TSeason.T_SEASON;
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
    public List<ContestRepo.UserContestView> findGeneralContestsByUserId(UUID userId) {
        return dsl.select(
                        T_CONTEST.PK_ID,
                        T_CONTEST.C_NAME,
                        T_SEASON.PK_ID,
                        T_SEASON.C_NAME,
                        T_SEASON.C_COMPLETED,
                        T_CONTEST.C_FROM_ROUND_POSITION,
                        T_CONTEST.C_TO_ROUND_POSITION)
                .from(T_CONTEST)
                .join(T_SEASON).on(T_SEASON.PK_ID.eq(T_CONTEST.FK_SEASON_ID))
                .join(T_ENTRY).on(T_ENTRY.FK_CONTEST_ID.eq(T_CONTEST.PK_ID)
                        .and(T_ENTRY.FK_USER_ID.eq(userId)))
                .where(T_CONTEST.C_IS_PRIVATE.eq(false))
                .and(T_CONTEST.C_FROM_ROUND_POSITION.eq(1))
                .and(T_CONTEST.C_TO_ROUND_POSITION.eq(T_SEASON.C_MAX_ROUNDS))
                .and(T_SEASON.C_MAX_ROUNDS.gt(0))
                .orderBy(T_SEASON.C_START_DATE.desc(), T_ENTRY.C_JOINED_AT_ROUND.desc())
                .fetch(r -> new ContestRepo.UserContestView(
                        r.get(T_CONTEST.PK_ID),
                        r.get(T_CONTEST.C_NAME),
                        r.get(T_SEASON.PK_ID),
                        r.get(T_SEASON.C_NAME),
                        Boolean.TRUE.equals(r.get(T_SEASON.C_COMPLETED)),
                        r.get(T_CONTEST.C_FROM_ROUND_POSITION),
                        r.get(T_CONTEST.C_TO_ROUND_POSITION),
                        false));
    }

    @Override
    public List<ContestRepo.UserContestView> findPrivateContestsByUserId(UUID userId) {
        return dsl.select(
                        T_CONTEST.PK_ID,
                        T_CONTEST.C_NAME,
                        T_SEASON.PK_ID,
                        T_SEASON.C_NAME,
                        T_SEASON.C_COMPLETED,
                        T_CONTEST.C_FROM_ROUND_POSITION,
                        T_CONTEST.C_TO_ROUND_POSITION)
                .from(T_CONTEST)
                .join(T_SEASON).on(T_SEASON.PK_ID.eq(T_CONTEST.FK_SEASON_ID))
                .join(T_ENTRY).on(T_ENTRY.FK_CONTEST_ID.eq(T_CONTEST.PK_ID)
                        .and(T_ENTRY.FK_USER_ID.eq(userId))
                        .and(T_ENTRY.C_REMOVED_AT_ROUND.isNull()))
                .where(T_CONTEST.C_IS_PRIVATE.eq(true))
                .orderBy(T_SEASON.C_START_DATE.desc(), T_ENTRY.C_JOINED_AT_ROUND.desc())
                .fetch(r -> new ContestRepo.UserContestView(
                        r.get(T_CONTEST.PK_ID),
                        r.get(T_CONTEST.C_NAME),
                        r.get(T_SEASON.PK_ID),
                        r.get(T_SEASON.C_NAME),
                        Boolean.TRUE.equals(r.get(T_SEASON.C_COMPLETED)),
                        r.get(T_CONTEST.C_FROM_ROUND_POSITION),
                        r.get(T_CONTEST.C_TO_ROUND_POSITION),
                        true));
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
