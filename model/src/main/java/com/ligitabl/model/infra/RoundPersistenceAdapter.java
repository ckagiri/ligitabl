package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TRound.T_ROUND;

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
                    .build();
        }
    }
}
