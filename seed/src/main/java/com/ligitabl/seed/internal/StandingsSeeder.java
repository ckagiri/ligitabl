package com.ligitabl.seed.internal;

import static com.ligitabl.model.db.tables.TSeason.T_SEASON;
import static com.ligitabl.model.db.tables.TStandings.T_STANDINGS;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.model.domain.StandingsMetadata;
import com.ligitabl.model.domain.StandingsTeamRank;
import com.ligitabl.model.domain.TeamRank;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.JSONB;

public class StandingsSeeder extends AbstractSeeder<DefaultsConfig> {

    private final ObjectMapper objectMapper;
    private final ReferenceResolver referenceResolver;

    public StandingsSeeder(DSLContext dsl, ObjectMapper objectMapper) {
        super(dsl);
        this.objectMapper = objectMapper;
        this.referenceResolver = new ReferenceResolver(dsl);
    }

    @Override
    protected boolean isValidConfig(DefaultsConfig config) {
        return config != null;
    }

    @Override
    protected void performSeed(DefaultsConfig defaults) {
        defaults.validateRequired();

        UUID seasonId = referenceResolver.resolveSeasonId(defaults.competitionSlug(), defaults.seasonSlug());

        JSONB initialRankingsJson = dsl.select(T_SEASON.C_INITIAL_RANKINGS)
                .from(T_SEASON)
                .where(T_SEASON.PK_ID.eq(seasonId))
                .fetchOne(T_SEASON.C_INITIAL_RANKINGS);

        List<TeamRank> initialRankings = readTeamRanks(initialRankingsJson);
        if (initialRankings.isEmpty()) {
            throw new IllegalStateException(
                    "Season has no initial_rankings; cannot seed initial standings for seasonId=" + seasonId);
        }

        StandingsMetadata zero = new StandingsMetadata(0, 0, 0, 0, 0, 0, 0, 0);
        List<StandingsTeamRank> rankings = initialRankings.stream()
                .map(tr -> new StandingsTeamRank(tr, zero))
                .toList();

        JSONB rankingsJson = writeRankings(rankings);

        int rows = dsl.insertInto(
                        T_STANDINGS,
                        T_STANDINGS.PK_ID,
                        T_STANDINGS.FK_SEASON_ID,
                        T_STANDINGS.C_ROUND_POSITION,
                        T_STANDINGS.C_RANKINGS,
                        T_STANDINGS.C_FINALISED,
                        T_STANDINGS.C_FINALISED_AT)
                .values(UUID.randomUUID(), seasonId, 1, rankingsJson, false, null)
                .onConflict(T_STANDINGS.FK_SEASON_ID, T_STANDINGS.C_ROUND_POSITION)
                .doNothing()
                .execute();

        if (rows > 0) {
            recordInsert();
        } else {
            recordSkip();
        }
    }

    private List<TeamRank> readTeamRanks(JSONB jsonb) {
        if (jsonb == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(jsonb.data(), new TypeReference<List<TeamRank>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialize season initial_rankings JSON", e);
        }
    }

    private JSONB writeRankings(List<StandingsTeamRank> rankings) {
        try {
            return JSONB.valueOf(objectMapper.writeValueAsString(rankings));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize standings rankings JSON", e);
        }
    }

    @Override
    protected String getSeederName() {
        return "standings";
    }
}
