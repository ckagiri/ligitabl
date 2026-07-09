package com.ligitabl.seed.internal;

import static com.ligitabl.model.db.tables.TSeason.T_SEASON;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record3;

/**
 * Resolves the "current" season for a competition from t_season date ranges, so seeding
 * config doesn't need to hardcode a season slug that goes stale every time a new season
 * is added.
 */
public class CurrentSeasonResolver {

    private final DSLContext dsl;
    private final ReferenceResolver referenceResolver;
    private final Clock clock;

    public CurrentSeasonResolver(DSLContext dsl) {
        this(dsl, Clock.systemDefaultZone());
    }

    public CurrentSeasonResolver(DSLContext dsl, Clock clock) {
        this.dsl = dsl;
        this.referenceResolver = new ReferenceResolver(dsl);
        this.clock = clock;
    }

    /**
     * Picks the season whose [startDate, endDate] contains today, else the soonest
     * upcoming season, else the most recently ended one.
     */
    public String resolveCurrentSeasonSlug(String competitionSlug) {
        UUID competitionId = referenceResolver.resolveCompetitionId(competitionSlug);

        List<Record3<String, LocalDate, LocalDate>> seasons = dsl
                .select(T_SEASON.C_SLUG, T_SEASON.C_START_DATE, T_SEASON.C_END_DATE)
                .from(T_SEASON)
                .where(T_SEASON.FK_COMPETITION_ID.eq(competitionId))
                .fetch();

        if (seasons.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot resolve current season: no seasons found for competitionSlug='"
                            + competitionSlug + "'");
        }

        LocalDate today = LocalDate.now(clock);

        for (Record3<String, LocalDate, LocalDate> season : seasons) {
            LocalDate start = season.get(T_SEASON.C_START_DATE);
            LocalDate end = season.get(T_SEASON.C_END_DATE);
            if (!today.isBefore(start) && !today.isAfter(end)) {
                return season.get(T_SEASON.C_SLUG);
            }
        }

        String soonestUpcomingSlug = null;
        LocalDate soonestUpcomingStart = null;
        for (Record3<String, LocalDate, LocalDate> season : seasons) {
            LocalDate start = season.get(T_SEASON.C_START_DATE);
            if (start.isAfter(today) && (soonestUpcomingStart == null || start.isBefore(soonestUpcomingStart))) {
                soonestUpcomingSlug = season.get(T_SEASON.C_SLUG);
                soonestUpcomingStart = start;
            }
        }
        if (soonestUpcomingSlug != null) {
            return soonestUpcomingSlug;
        }

        // Everything remaining has end < today (didn't contain today, didn't start after today).
        String mostRecentlyEndedSlug = null;
        LocalDate mostRecentlyEndedEnd = null;
        for (Record3<String, LocalDate, LocalDate> season : seasons) {
            LocalDate end = season.get(T_SEASON.C_END_DATE);
            if (mostRecentlyEndedEnd == null || end.isAfter(mostRecentlyEndedEnd)) {
                mostRecentlyEndedSlug = season.get(T_SEASON.C_SLUG);
                mostRecentlyEndedEnd = end;
            }
        }
        return mostRecentlyEndedSlug;
    }
}
