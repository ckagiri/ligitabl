package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TCompetition.T_COMPETITION;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.RecordMapper;

import com.ligitabl.model.db.tables.records.CompetitionRecord;
import com.ligitabl.model.domain.Competition;
import com.ligitabl.model.domain.CompetitionSlug;
import com.ligitabl.model.repo.CompetitionRepo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CompetitionPersistenceAdapter implements CompetitionRepo {
    private final DSLContext dsl;

    private static final CompetitionRecordMapper MAPPER = new CompetitionRecordMapper();

    @Override
    public Optional<Competition> findById(UUID id) {
        var record =
                dsl.selectFrom(T_COMPETITION).where(T_COMPETITION.PK_ID.eq(id)).fetchOne();

        return Optional.ofNullable(MAPPER.map(record));
    }

    @Override
    public List<Competition> findAll() {
        return dsl.selectFrom(T_COMPETITION)
                .orderBy(T_COMPETITION.C_NAME.asc())
                .fetch()
                .map(MAPPER::map);
    }

    @Override
    public Optional<Competition> findBySlug(CompetitionSlug slug) {
        var record =
                dsl.selectFrom(T_COMPETITION).where(T_COMPETITION.C_SLUG.eq(slug.value())).fetchOne();

        return Optional.ofNullable(MAPPER.map(record));
    }

    @Override
    public boolean existsById(UUID id) {
        return dsl.fetchExists(
                dsl.selectOne().from(T_COMPETITION).where(T_COMPETITION.PK_ID.eq(id)));
    }

    private static class CompetitionRecordMapper
            implements RecordMapper<CompetitionRecord, Competition> {
        @Override
        public Competition map(CompetitionRecord record) {
            if (record == null) {
                return null;
            }

            return Competition.builder()
                    .id(record.getId())
                    .name(record.getName())
                    .slug(CompetitionSlug.of(record.getSlug()))
                    .code(record.getCode())
                    .activeSeasonId(record.getActiveSeasonId())
                    .build();
        }
    }
}
