package com.ligitabl.seed.internal;

import static com.ligitabl.model.db.tables.TCompetition.T_COMPETITION;
import static com.ligitabl.model.db.tables.TContest.T_CONTEST;
import static com.ligitabl.model.db.tables.TRound.T_ROUND;
import static com.ligitabl.model.db.tables.TSeason.T_SEASON;

import java.util.UUID;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;

public class ContestSeeder extends AbstractSeeder<CurrentSeason> {

    private final ReferenceResolver referenceResolver;

    public ContestSeeder(DSLContext dsl) {
        super(dsl);
        this.referenceResolver = new ReferenceResolver(dsl);
    }

    @Override
    protected boolean isValidConfig(CurrentSeason config) {
        return config != null;
    }

    @Override
    protected void performSeed(CurrentSeason currentSeason) {
        UUID seasonId = referenceResolver.resolveSeasonId(currentSeason.competitionSlug(), currentSeason.seasonSlug());

        String competitionName = dsl.select(T_COMPETITION.C_NAME)
                .from(T_COMPETITION)
                .where(T_COMPETITION.C_SLUG.eq(currentSeason.competitionSlug()))
                .fetchOne(T_COMPETITION.C_NAME);

        String seasonName = dsl.select(T_SEASON.C_NAME)
                .from(T_SEASON)
                .where(T_SEASON.PK_ID.eq(seasonId))
                .fetchOne(T_SEASON.C_NAME);

        String contestName = String.format("%s %s", competitionName, seasonName).trim();

        Integer maxRounds = dsl.select(T_SEASON.C_MAX_ROUNDS)
                .from(T_SEASON)
                .where(T_SEASON.PK_ID.eq(seasonId))
                .fetchOne(T_SEASON.C_MAX_ROUNDS);

        int toRoundPosition = maxRounds != null && maxRounds > 0
                ? maxRounds
                : dsl.select(DSL.max(T_ROUND.C_POSITION))
                        .from(T_ROUND)
                        .where(T_ROUND.FK_SEASON_ID.eq(seasonId))
                        .fetchOne(0, int.class);

        boolean alreadyExists = dsl.fetchExists(
                dsl.selectOne()
                        .from(T_CONTEST)
                        .where(T_CONTEST.FK_SEASON_ID.eq(seasonId).and(T_CONTEST.C_NAME.eq(contestName))));

        // Ensure contest exists (idempotent), then wire it onto season as main contest.
        if (!alreadyExists) {
            dsl.insertInto(
                            T_CONTEST,
                            T_CONTEST.PK_ID,
                            T_CONTEST.FK_SEASON_ID,
                            T_CONTEST.C_NAME,
                            T_CONTEST.C_IS_PRIVATE,
                            T_CONTEST.C_JOIN_CODE,
                            T_CONTEST.C_FROM_ROUND_POSITION,
                            T_CONTEST.C_TO_ROUND_POSITION,
                            T_CONTEST.C_MAX_ENTRIES)
                    .values(
                            UUID.randomUUID(),
                            seasonId,
                            contestName,
                            false,
                            null,
                            1,
                            toRoundPosition,
                            null)
                    .onConflictDoNothing()
                    .execute();
        }

        UUID contestId = dsl.select(T_CONTEST.PK_ID)
                .from(T_CONTEST)
                .where(T_CONTEST.FK_SEASON_ID.eq(seasonId).and(T_CONTEST.C_NAME.eq(contestName)))
                .fetchOne(T_CONTEST.PK_ID);

        if (contestId == null) {
            throw new IllegalStateException(
                    "Failed to resolve main contest after insert for season=" + seasonId + ", name='" + contestName + "'");
        }

        dsl.update(T_SEASON)
                .set(T_SEASON.FK_MAIN_CONTEST_ID, contestId)
                .where(T_SEASON.PK_ID.eq(seasonId))
                .execute();

                if (alreadyExists) {
                        recordSkip();
                } else {
                        recordInsert();
                }
    }

    @Override
    protected String getSeederName() {
        return "contest";
    }
}
