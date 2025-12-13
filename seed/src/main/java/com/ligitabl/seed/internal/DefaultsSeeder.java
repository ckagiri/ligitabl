package com.ligitabl.seed.internal;

import static com.ligitabl.model.db.tables.TCompetition.T_COMPETITION;
import static com.ligitabl.model.db.tables.TSeason.T_SEASON;
import static com.ligitabl.model.db.tables.TRound.T_ROUND;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;

public class DefaultsSeeder {

    private final DSLContext dsl;

    public DefaultsSeeder(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void applyDefaults() {
        // Set fk_active_season_id = latest season per competition (by start date)
        dsl.update(T_COMPETITION)
                .set(
                        T_COMPETITION.FK_ACTIVE_SEASON_ID,
                        DSL.select(T_SEASON.PK_ID)
                                .from(T_SEASON)
                                .where(T_SEASON.FK_COMPETITION_ID.eq(T_COMPETITION.PK_ID))
                                .orderBy(T_SEASON.C_START_DATE.desc())
                                .limit(1))
                .execute();

        // Set fk_current_round_id = first round per season (by position)
        dsl.update(T_SEASON)
                .set(
                        T_SEASON.FK_CURRENT_ROUND_ID,
                        DSL.select(T_ROUND.PK_ID)
                                .from(T_ROUND)
                                .where(T_ROUND.FK_SEASON_ID.eq(T_SEASON.PK_ID))
                                .orderBy(T_ROUND.C_POSITION.asc())
                                .limit(1))
                .execute();
    }
}
