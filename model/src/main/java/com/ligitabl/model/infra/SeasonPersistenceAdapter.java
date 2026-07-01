package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TCompetition.T_COMPETITION;
import static com.ligitabl.model.db.tables.TSeason.T_SEASON;

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
    public Season save(Season season) {
        if (season == null) {
            throw new IllegalArgumentException("Season must not be null");
        }
        if (season.getId() == null) {
            throw new IllegalArgumentException("Season.id must not be null on save");
        }

        int updated = dsl.update(T_SEASON)
                .set(T_SEASON.C_CLIENT_ID, season.getClientId())
                .set(T_SEASON.FK_COMPETITION_ID, season.getCompetitionId())
                .set(T_SEASON.C_NAME, season.getName())
                .set(
                        T_SEASON.C_SLUG,
                        season.getSlug() == null ? null : season.getSlug().value())
                .set(T_SEASON.C_START_DATE, season.getStartDate())
                .set(T_SEASON.C_END_DATE, season.getEndDate())
                .set(T_SEASON.C_MAX_ROUNDS, season.getMaxRounds())
                .set(T_SEASON.C_COMPLETED, season.isCompleted())
                .set(T_SEASON.C_COMPLETED_AT, season.getCompletedAt())
                .set(T_SEASON.C_TOTAL_TEAMS, season.getTotalTeams())
                .set(T_SEASON.C_MAX_HIT_POINTS, season.getMaxHitPoints())
                .set(T_SEASON.C_INITIAL_RANKINGS, writeTeams(season.getInitialRankings()))
                .set(T_SEASON.FK_MAIN_CONTEST_ID, season.getMainContestId())
                .set(T_SEASON.FK_CURRENT_ROUND_ID, season.getCurrentRoundId())
                .set(T_SEASON.C_CURRENT_MATCH_DAY, season.getCurrentMatchDay())
                .set(T_SEASON.FK_DETACHED_CONTEST_ID, season.getDetachedContestId())
                .set(T_SEASON.C_PRE_SEASON_OPENS_AT, season.getPreSeasonOpensAt())
                .set(T_SEASON.C_PREDICTIONS_OPEN_AT, season.getPredictionsOpenAt())
                .where(T_SEASON.PK_ID.eq(season.getId()))
                .execute();

        if (updated == 0) {
            throw new NoSuchElementException(String.format("Season with id %s not found", season.getId()));
        }

        return findById(season.getId()).orElseThrow(() -> new NoSuchElementException("Season not found after save"));
    }

    @Override
    public Optional<Season> findActiveSeason(String competitionSlugOrCode) {
        if (competitionSlugOrCode == null || competitionSlugOrCode.isBlank()) {
            throw new IllegalArgumentException("competitionSlugOrCode must not be blank");
        }

        SeasonRecord record = dsl.select(T_SEASON.fields())
                .from(T_SEASON)
                .join(T_COMPETITION)
                .on(T_SEASON.FK_COMPETITION_ID.eq(T_COMPETITION.PK_ID))
                .where(T_COMPETITION
                        .C_SLUG
                        .eq(competitionSlugOrCode)
                        .or(T_COMPETITION.C_CODE.eq(competitionSlugOrCode)))
                .and(T_SEASON.C_COMPLETED.eq(false))
                .orderBy(T_SEASON.C_START_DATE.desc())
                .limit(1)
                .fetchOneInto(SeasonRecord.class);

        return Optional.ofNullable(MAPPER.map(record));
    }

    @Override
    public Optional<Season> findMostRecentSeason(String competitionSlug) {
        if (competitionSlug == null || competitionSlug.isBlank()) {
            throw new IllegalArgumentException("competitionSlug must not be blank");
        }

        SeasonRecord record = dsl.select(T_SEASON.fields())
                .from(T_SEASON)
                .join(T_COMPETITION)
                .on(T_SEASON.FK_COMPETITION_ID.eq(T_COMPETITION.PK_ID))
                .where(T_COMPETITION.C_SLUG.eq(competitionSlug))
                .orderBy(T_SEASON.C_START_DATE.desc())
                .limit(1)
                .fetchOneInto(SeasonRecord.class);

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

    @Override
    public Optional<Season> findActiveSeason(UUID competitionId) {
        if (competitionId == null) {
            throw new IllegalArgumentException("competitionId must not be null");
        }

        var record = dsl.selectFrom(T_SEASON)
                .where(T_SEASON.FK_COMPETITION_ID.eq(competitionId).and(T_SEASON.C_COMPLETED.eq(false)))
                .orderBy(T_SEASON.C_START_DATE.desc())
                .limit(1)
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
                    .detachedContestId(record.getDetachedContestId())
                    .preSeasonOpensAt(record.getPreSeasonOpensAt())
                    .predictionsOpenAt(record.getPredictionsOpenAt())
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

    private static JSONB writeTeams(List<TeamRank> rankings) {
        if (rankings == null || rankings.isEmpty()) {
            return null;
        }
        try {
            return JSONB.valueOf(OBJECT_MAPPER.writeValueAsString(rankings));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize season teams JSON", e);
        }
    }
}
