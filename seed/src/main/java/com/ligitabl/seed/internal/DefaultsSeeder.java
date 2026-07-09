package com.ligitabl.seed.internal;

import static com.ligitabl.model.db.tables.TCompetition.T_COMPETITION;
import static com.ligitabl.model.db.tables.TSeason.T_SEASON;
import static com.ligitabl.model.db.tables.TRound.T_ROUND;

import java.util.UUID;
import org.jooq.DSLContext;

public class DefaultsSeeder {

    private final DSLContext dsl;

    public DefaultsSeeder(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Sets fk_active_season_id / fk_current_round_id only if they're currently null.
     * Once a competition/season has these wired, further round progression and season
     * activation are the application's job (match-day workflows, admin actions) — seeding
     * must never clobber that live state on a re-run.
     */
    public DefaultsApplyResult applyDefaults(CurrentSeason currentSeason) {
        var competitionId = dsl.select(T_COMPETITION.PK_ID)
                .from(T_COMPETITION)
                .where(T_COMPETITION.C_SLUG.eq(currentSeason.competitionSlug()))
                .fetchOne(T_COMPETITION.PK_ID);

        if (competitionId == null) {
            throw new IllegalStateException(
                    "Defaults competition not found with slug: '" + currentSeason.competitionSlug() + "'");
        }

        var seasonId = dsl.select(T_SEASON.PK_ID)
                .from(T_SEASON)
                .where(T_SEASON.FK_COMPETITION_ID.eq(competitionId)
                        .and(T_SEASON.C_SLUG.eq(currentSeason.seasonSlug())))
                .fetchOne(T_SEASON.PK_ID);

        if (seasonId == null) {
            throw new IllegalStateException(
                    "Defaults season not found with competitionSlug='" + currentSeason.competitionSlug()
                            + "', seasonSlug='" + currentSeason.seasonSlug() + "'");
        }

        var roundId = dsl.select(T_ROUND.PK_ID)
                .from(T_ROUND)
                .where(T_ROUND.FK_SEASON_ID.eq(seasonId).and(T_ROUND.C_POSITION.eq(1)))
                .fetchOne(T_ROUND.PK_ID);

        if (roundId == null) {
            throw new IllegalStateException(
                    "Defaults current round not found for seasonId=" + seasonId + " (expected position=1)");
        }

        UUID existingActiveSeasonId = dsl.select(T_COMPETITION.FK_ACTIVE_SEASON_ID)
                .from(T_COMPETITION)
                .where(T_COMPETITION.PK_ID.eq(competitionId))
                .fetchOne(T_COMPETITION.FK_ACTIVE_SEASON_ID);

        boolean activeSeasonSet = existingActiveSeasonId == null;
        if (activeSeasonSet) {
            dsl.update(T_COMPETITION)
                    .set(T_COMPETITION.FK_ACTIVE_SEASON_ID, seasonId)
                    .where(T_COMPETITION.PK_ID.eq(competitionId))
                    .execute();
        }

        UUID existingCurrentRoundId = dsl.select(T_SEASON.FK_CURRENT_ROUND_ID)
                .from(T_SEASON)
                .where(T_SEASON.PK_ID.eq(seasonId))
                .fetchOne(T_SEASON.FK_CURRENT_ROUND_ID);

        boolean currentRoundSet = existingCurrentRoundId == null;
        if (currentRoundSet) {
            dsl.update(T_SEASON)
                    .set(T_SEASON.FK_CURRENT_ROUND_ID, roundId)
                    .where(T_SEASON.PK_ID.eq(seasonId))
                    .execute();
        }

        return new DefaultsApplyResult(activeSeasonSet, currentRoundSet);
    }

    public record DefaultsApplyResult(boolean activeSeasonSet, boolean currentRoundSet) {}
}
