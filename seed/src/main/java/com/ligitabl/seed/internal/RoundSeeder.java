package com.ligitabl.seed.internal;

import static com.ligitabl.model.db.tables.TRound.T_ROUND;

import com.ligitabl.seed.internal.config.RoundSeedConfig;
import java.util.UUID;
import org.jooq.DSLContext;

public class RoundSeeder extends AbstractSeeder<RoundSeedConfig> {

    private final ReferenceResolver referenceResolver;

    public RoundSeeder(DSLContext dsl) {
        super(dsl);
        this.referenceResolver = new ReferenceResolver(dsl);
    }

    @Override
    protected boolean isValidConfig(RoundSeedConfig config) {
        return config != null && config.getCount() > 0;
    }

    @Override
    protected void performSeed(RoundSeedConfig config) {
        UUID seasonId = referenceResolver.resolveSeasonId(
                config.getCompetitionSlug(), config.getSeasonSlug());

        for (int i = 0; i < config.getCount(); i++) {
            seedRound(seasonId, config, i);
        }
    }

    @Override
    protected String getSeederName() {
        return "round";
    }

    private void seedRound(UUID seasonId, RoundSeedConfig config, int index) {
        int position = config.getStartPosition() + index;
        String name = config.getNamePrefix() + position;
        String slug = config.getSlugPrefix() + position;

        if (roundExists(seasonId, position)) {
            recordSkip();
            return;
        }

        int rowsAffected = dsl.insertInto(
                        T_ROUND,
                        T_ROUND.PK_ID,
                        T_ROUND.FK_SEASON_ID,
                        T_ROUND.C_NAME,
                        T_ROUND.C_SLUG,
                        T_ROUND.C_POSITION)
                .values(UUID.randomUUID(), seasonId, name, slug, position)
                .execute();

        if (rowsAffected > 0) {
            recordInsert();
        }
    }

    private boolean roundExists(UUID seasonId, int position) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(T_ROUND)
                        .where(T_ROUND.FK_SEASON_ID.eq(seasonId)
                                .and(T_ROUND.C_POSITION.eq(position))));
    }
}

