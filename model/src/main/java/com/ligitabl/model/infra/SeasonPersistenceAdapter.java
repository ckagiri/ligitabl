package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TSeason.T_SEASON;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.RecordMapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.model.db.tables.records.SeasonRecord;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;
import com.ligitabl.model.domain.TeamRank;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SeasonPersistenceAdapter implements SeasonRepo {
    private final DSLContext dsl;

    private static final SeasonRecordMapper MAPPER = new SeasonRecordMapper();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Optional<Season> findById(UUID id) {
        var record = dsl.selectFrom(T_SEASON).where(T_SEASON.PK_ID.eq(id)).fetchOne();

        return Optional.ofNullable(MAPPER.map(record));
    }

    @Override
    public Optional<Season> findByClientId(Integer clientId) {
        var record = dsl.selectFrom(T_SEASON)
                .where(T_SEASON.C_CLIENT_ID.eq(clientId))
                .fetchOne();

        return Optional.ofNullable(MAPPER.map(record));
    }

    @Override
    public boolean existsById(UUID id) {
        return dsl.fetchExists(dsl.selectOne().from(T_SEASON).where(T_SEASON.PK_ID.eq(id)));
    }

    @Override
    public List<Season> findAllByCompetitionId(UUID competitionId) {
        return dsl.selectFrom(T_SEASON)
                .where(T_SEASON.FK_COMPETITION_ID.eq(competitionId))
                .orderBy(T_SEASON.C_START_DATE.asc())
                .fetch()
                .map(MAPPER::map);
    }

    @Override
    public Optional<Season> findByCompetitionIdAndSlug(UUID competitionId, SeasonSlug slug) {
        var record = dsl.selectFrom(T_SEASON)
                .where(T_SEASON.FK_COMPETITION_ID.eq(competitionId).and(T_SEASON.C_SLUG.eq(slug.value())))
                .fetchOne();

        return Optional.ofNullable(MAPPER.map(record));
    }

    private static class SeasonRecordMapper implements RecordMapper<SeasonRecord, Season> {
        @Override
        public Season map(SeasonRecord record) {
            if (record == null) {
                return null;
            }

            return Season.builder()
                    .id(record.getId())
                    .clientId(record.getClientId())
                    .competitionId(record.getCompetitionId())
                    .name(record.getName())
                    .slug(SeasonSlug.of(record.getSlug()))
                    .startDate(record.getStartDate())
                    .endDate(record.getEndDate())
                    .maxRounds(record.getMaxRounds())
                    .completed(Boolean.TRUE.equals(record.getCompleted()))
                    .completedAt(record.getCompletedAt())
                    .totalTeams(record.getTotalTeams())
                    .maxHitPoints(record.getMaxHitPoints())
                    .currentRoundId(record.getCurrentRoundId())
                    .currentMatchDay(record.getCurrentMatchDay())
                    .initialRankings(readTeams(record.getInitialRankings()))
                    .mainContestId(record.getMainContestId())
                    .build();
        }

        private static List<TeamRank> readTeams(JSONB jsonb) {
            if (jsonb == null) {
                return List.of();
            }

            try {
                return OBJECT_MAPPER.readValue(jsonb.data(), new TypeReference<List<TeamRank>>() {});
            } catch (IOException e) {
                throw new IllegalStateException("Failed to deserialize season teams JSON", e);
            }
        }
    }
}
