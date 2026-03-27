package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TRound.T_ROUND;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.RecordMapper;

import com.ligitabl.model.db.tables.records.RoundRecord;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.repo.RoundRepo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RoundPersistenceAdapter implements RoundRepo {
    private final DSLContext dsl;

    private static final RoundRecordMapper MAPPER = new RoundRecordMapper();

    @Override
    public Optional<Round> findById(UUID id) {
        var record = dsl.selectFrom(T_ROUND).where(T_ROUND.PK_ID.eq(id)).fetchOne();

        return Optional.ofNullable(MAPPER.map(record));
    }

    @Override
    public List<Round> findBySeasonId(UUID seasonId) {
        return dsl.selectFrom(T_ROUND)
                .where(T_ROUND.FK_SEASON_ID.eq(seasonId))
                .orderBy(T_ROUND.C_POSITION.asc())
                .fetch()
                .map(MAPPER::map);
    }

    @Override
    public Optional<Round> findBySeasonIdAndPosition(UUID seasonId, int position) {
        var record = dsl.selectFrom(T_ROUND)
                .where(T_ROUND.FK_SEASON_ID.eq(seasonId).and(T_ROUND.C_POSITION.eq(position)))
                .fetchOne();

        return Optional.ofNullable(MAPPER.map(record));
    }

    @Override
    public List<Round> findBySeasonIdOrderByPosition(UUID seasonId) {
        return dsl.selectFrom(T_ROUND)
                .where(T_ROUND.FK_SEASON_ID.eq(seasonId))
                .orderBy(T_ROUND.C_POSITION.asc())
                .fetch()
                .map(MAPPER::map);
    }

    @Override
    public List<Round> findMissedAdvancements(OffsetDateTime now) {
        return dsl.selectFrom(T_ROUND)
                .where(T_ROUND.C_IS_FINALIZED.eq(true)
                        .and(T_ROUND.C_ADVANCED.eq(false))
                        .and(T_ROUND.C_ADVANCE_AT.isNotNull())
                        .and(T_ROUND.C_ADVANCE_AT.le(now)))
                .fetch()
                .map(MAPPER::map);
    }

    @Override
    public Round save(Round round) {
        if (round == null) {
            throw new IllegalArgumentException("Round must not be null");
        }

        return round.getId() == null ? create(round) : update(round);
    }

    private Round create(Round round) {
        if (round.getId() != null) {
            throw new IllegalArgumentException(
                    String.format("Round.id must be null on create (received %s)", round.getId()));
        }

        UUID id = UUID.randomUUID();
        dsl.insertInto(T_ROUND)
                .set(T_ROUND.PK_ID, id)
                .set(T_ROUND.FK_SEASON_ID, round.getSeasonId())
                .set(T_ROUND.C_NAME, round.getName())
                .set(T_ROUND.C_SLUG, round.getSlug())
                .set(T_ROUND.C_POSITION, round.getPosition())
                .set(T_ROUND.C_IS_FINALIZED, round.isFinalized())
                .set(T_ROUND.C_ADVANCE_AT, round.getAdvanceAt())
                .set(T_ROUND.C_ADVANCED, round.isAdvanced())
                .set(T_ROUND.C_ADVANCED_AT, round.getAdvancedAt())
                .execute();

        return findById(id).orElseThrow(() -> new IllegalStateException("Round not found after create"));
    }

    private Round update(Round round) {
        if (round.getId() == null) {
            throw new IllegalArgumentException("Round.id must not be null on update");
        }

        int updated = dsl.update(T_ROUND)
                .set(T_ROUND.FK_SEASON_ID, round.getSeasonId())
                .set(T_ROUND.C_NAME, round.getName())
                .set(T_ROUND.C_SLUG, round.getSlug())
                .set(T_ROUND.C_POSITION, round.getPosition())
                .set(T_ROUND.C_IS_FINALIZED, round.isFinalized())
                .set(T_ROUND.C_ADVANCE_AT, round.getAdvanceAt())
                .set(T_ROUND.C_ADVANCED, round.isAdvanced())
                .set(T_ROUND.C_ADVANCED_AT, round.getAdvancedAt())
                .where(T_ROUND.PK_ID.eq(round.getId()))
                .execute();

        if (updated == 0) {
            throw new IllegalStateException(String.format("Round with id %s not found", round.getId()));
        }

        return findById(round.getId()).orElseThrow(() -> new IllegalStateException("Round not found after update"));
    }

    private static class RoundRecordMapper implements RecordMapper<RoundRecord, Round> {
        @Override
        public Round map(RoundRecord record) {
            if (record == null) {
                return null;
            }

            return Round.builder()
                    .id(record.getId())
                    .seasonId(record.getSeasonId())
                    .name(record.getName())
                    .slug(record.getSlug())
                    .position(record.getPosition())
                    .finalized(Boolean.TRUE.equals(record.getIsFinalized()))
                    .advanceAt(record.getAdvanceAt())
                    .advanced(Boolean.TRUE.equals(record.getAdvanced()))
                    .advancedAt(record.getAdvancedAt())
                    .build();
        }
    }
}
