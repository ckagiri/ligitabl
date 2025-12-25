package com.ligitabl.model.infra;

import static com.ligitabl.model.db.tables.TStandings.T_STANDINGS;

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
import com.ligitabl.model.db.tables.records.StandingsRecord;
import com.ligitabl.model.domain.Standings;
import com.ligitabl.model.domain.StandingsTeamRank;
import com.ligitabl.model.repo.StandingsRepo;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StandingsPersistenceAdapter implements StandingsRepo {
    private final DSLContext dsl;

    private static final StandingsRecordMapper MAPPER = new StandingsRecordMapper();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Optional<Standings> findBySeasonAndRoundPosition(UUID seasonId, int roundPosition) {
        var record = dsl.selectFrom(T_STANDINGS)
                .where(T_STANDINGS.FK_SEASON_ID.eq(seasonId).and(T_STANDINGS.C_ROUND_POSITION.eq(roundPosition)))
                .fetchOne();

        return Optional.ofNullable(MAPPER.map(record));
    }

    @Override
    public Optional<Standings> findById(UUID id) {
        var record = dsl.selectFrom(T_STANDINGS).where(T_STANDINGS.PK_ID.eq(id)).fetchOne();

        return Optional.ofNullable(MAPPER.map(record));
    }

    @Override
    public Standings create(Standings model) {
        if (model.getId() != null) {
            throw new IllegalArgumentException(
                    String.format("Standings.id must be null on create (received %s)", model.getId()));
        }

        UUID id = UUID.randomUUID();
        JSONB rankingsJson = writeRankings(model.getRankings());

        dsl.insertInto(T_STANDINGS)
                .set(T_STANDINGS.PK_ID, id)
                .set(T_STANDINGS.FK_SEASON_ID, model.getSeasonId())
                .set(T_STANDINGS.C_ROUND_POSITION, model.getRoundPosition())
                .set(T_STANDINGS.C_RANKINGS, rankingsJson)
                .set(T_STANDINGS.C_FINALISED, model.isFinalised())
                .set(T_STANDINGS.C_FINALISED_AT, model.getFinalisedAt())
                .execute();

        return findById(id).orElseThrow(() -> new NoSuchElementException("Failed to create standings"));
    }

    @Override
    public Standings update(Standings model) {
        if (model.getId() == null) {
            throw new IllegalArgumentException("Standings.id must not be null on update");
        }

        int updated = dsl.update(T_STANDINGS)
                .set(T_STANDINGS.FK_SEASON_ID, model.getSeasonId())
                .set(T_STANDINGS.C_ROUND_POSITION, model.getRoundPosition())
                .set(T_STANDINGS.C_RANKINGS, writeRankings(model.getRankings()))
                .set(T_STANDINGS.C_FINALISED, model.isFinalised())
                .set(T_STANDINGS.C_FINALISED_AT, model.getFinalisedAt())
                .where(T_STANDINGS.PK_ID.eq(model.getId()))
                .execute();

        if (updated == 0) {
            throw new NoSuchElementException(String.format("Standings with id %s not found", model.getId()));
        }

        return findById(model.getId())
                .orElseThrow(() -> new NoSuchElementException("Standings not found after update"));
    }

    @Override
    public void delete(UUID id) {
        dsl.deleteFrom(T_STANDINGS).where(T_STANDINGS.PK_ID.eq(id)).execute();
    }

    private static List<StandingsTeamRank> readRankings(JSONB jsonb) {
        if (jsonb == null) return List.of();
        try {
            return OBJECT_MAPPER.readValue(jsonb.data(), new TypeReference<List<StandingsTeamRank>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialize standings rankings JSON", e);
        }
    }

    private static JSONB writeRankings(List<StandingsTeamRank> rankings) {
        if (rankings == null || rankings.isEmpty()) return null;
        try {
            String json = OBJECT_MAPPER.writeValueAsString(rankings);
            return JSONB.valueOf(json);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize standings rankings JSON", e);
        }
    }

    private static class StandingsRecordMapper implements RecordMapper<StandingsRecord, Standings> {
        @Override
        public Standings map(StandingsRecord record) {
            if (record == null) {
                return null;
            }

            return Standings.builder()
                    .id(record.getId())
                    .seasonId(record.getSeasonId())
                    .roundPosition(record.getRoundPosition())
                    .rankings(readRankings(record.getRankings()))
                    .finalised(Boolean.TRUE.equals(record.getFinalised()))
                    .finalisedAt(record.getFinalisedAt())
                    .build();
        }
    }
}
