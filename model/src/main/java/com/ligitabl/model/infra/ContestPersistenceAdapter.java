package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TContest.T_CONTEST;
import static com.ligitabl.model.db.tables.TEntry.T_ENTRY;
import static com.ligitabl.model.db.tables.TSeason.T_SEASON;
import static org.jooq.impl.DSL.upper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectConditionStep;
import org.jooq.impl.DSL;

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
    public List<Contest> findPrivateByUserId(UUID userId, UUID seasonId) {
        return dsl.selectFrom(T_CONTEST)
                .where(T_CONTEST.C_IS_PRIVATE.eq(true))
                .and(T_CONTEST.FK_SEASON_ID.eq(seasonId))
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
    public List<ContestRepo.UserContestView> findContestsByUserId(
            UUID userId, UUID activeSeasonId, boolean activeTab, int limit, int offset) {
        var generals = unionBranchForContests(userId, activeSeasonId, activeTab, false);
        var privates = unionBranchForContests(userId, activeSeasonId, activeTab, true);

        return generals.unionAll(privates)
                .orderBy(
                        DSL.field("sort_group"),
                        DSL.field("season_start_date").desc(),
                        DSL.field("joined_at_round").desc())
                .limit(limit)
                .offset(offset)
                .fetch(r -> new ContestRepo.UserContestView(
                        r.get("contest_id", UUID.class),
                        r.get("contest_name", String.class),
                        r.get("season_id", UUID.class),
                        r.get("season_name", String.class),
                        Boolean.TRUE.equals(r.get("season_completed", Boolean.class)),
                        r.get("from_round", Integer.class),
                        r.get("to_round", Integer.class),
                        Boolean.TRUE.equals(r.get("is_private", Boolean.class))));
    }

    @Override
    public int countContestsByUserId(UUID userId, UUID activeSeasonId, boolean activeTab) {
        var generals = unionBranchForContests(userId, activeSeasonId, activeTab, false);
        var privates = unionBranchForContests(userId, activeSeasonId, activeTab, true);

        Integer count =
                dsl.selectCount().from(generals.unionAll(privates).asTable("t")).fetchOne(0, Integer.class);
        return count != null ? count : 0;
    }

    private SelectConditionStep<Record> unionBranchForContests(
            UUID userId, UUID activeSeasonId, boolean activeTab, boolean isPrivate) {
        // Use List<Field<?>> overload so jOOQ returns SelectConditionStep<Record> instead of
        // the strongly-typed SelectConditionStep<Record11<...>> that the varargs overload produces.
        var fields = List.<org.jooq.Field<?>>of(
                DSL.inline(isPrivate ? 1 : 0).as("sort_group"),
                T_CONTEST.PK_ID.as("contest_id"),
                T_CONTEST.C_NAME.as("contest_name"),
                T_SEASON.PK_ID.as("season_id"),
                T_SEASON.C_NAME.as("season_name"),
                T_SEASON.C_COMPLETED.as("season_completed"),
                T_CONTEST.C_FROM_ROUND_POSITION.as("from_round"),
                T_CONTEST.C_TO_ROUND_POSITION.as("to_round"),
                DSL.inline(isPrivate).as("is_private"),
                T_SEASON.C_START_DATE.as("season_start_date"),
                T_ENTRY.C_JOINED_AT_ROUND.as("joined_at_round"));

        var baseJoin = dsl.select(fields)
                .from(T_CONTEST)
                .join(T_SEASON)
                .on(T_SEASON.PK_ID.eq(T_CONTEST.FK_SEASON_ID))
                .join(T_ENTRY)
                .on(T_ENTRY.FK_CONTEST_ID.eq(T_CONTEST.PK_ID).and(T_ENTRY.FK_USER_ID.eq(userId)));

        // activeTab: contest's season is the competition's current active season.
        // past tab: any other season. If there is no active season, every season counts as past.
        var seasonScope = activeTab
                ? (activeSeasonId != null ? T_SEASON.PK_ID.eq(activeSeasonId) : DSL.falseCondition())
                : (activeSeasonId != null ? T_SEASON.PK_ID.ne(activeSeasonId) : DSL.trueCondition());

        if (!isPrivate) {
            return baseJoin.where(T_CONTEST.C_IS_PRIVATE.eq(false))
                    .and(T_CONTEST.C_FROM_ROUND_POSITION.eq(1))
                    .and(T_CONTEST.C_TO_ROUND_POSITION.eq(T_SEASON.C_MAX_ROUNDS))
                    .and(T_SEASON.C_MAX_ROUNDS.gt(0))
                    .and(seasonScope);
        } else {
            return baseJoin.where(T_CONTEST.C_IS_PRIVATE.eq(true))
                    .and(T_ENTRY.C_REMOVED_AT_ROUND.isNull())
                    .and(seasonScope);
        }
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
