package com.ligitabl.api.domain.season;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;

/** Shared fixture helpers for the Season_is*Test classes in this package. */
final class SeasonTestFixtures {

    private SeasonTestFixtures() {}

    static Season season(boolean completed, OffsetDateTime preSeasonOpensAt, OffsetDateTime predictionsOpenAt) {
        // A completed season's own start/end are naturally both in the past; an upcoming
        // (not-yet-started) season's are both in the future — matches getSeasonState()'s
        // PRE_SEASON/OFF_SEASON boundary checks (past endDate when completed, before startDate
        // when not). Use the overload below when a test needs to vary start/end independently.
        LocalDate startDate =
                completed ? LocalDate.now().minusMonths(9) : LocalDate.now().plusDays(1);
        LocalDate endDate =
                completed ? LocalDate.now().minusDays(1) : LocalDate.now().plusMonths(9);
        return season(completed, preSeasonOpensAt, predictionsOpenAt, startDate, endDate);
    }

    static Season season(
            boolean completed,
            OffsetDateTime preSeasonOpensAt,
            OffsetDateTime predictionsOpenAt,
            LocalDate startDate,
            LocalDate endDate) {
        return Season.builder()
                .clientId(1)
                .competitionId(UUID.randomUUID())
                .name("Test Season")
                .slug(SeasonSlug.of("2025-26"))
                .startDate(startDate)
                .endDate(endDate)
                .completed(completed)
                .preSeasonOpensAt(preSeasonOpensAt)
                .predictionsOpenAt(predictionsOpenAt)
                .build();
    }

    /** A relative point in time, named for parameterized-test readability instead of raw timestamps. */
    enum RelativeDate {
        NULL,
        PAST,
        FUTURE;

        OffsetDateTime resolve() {
            return switch (this) {
                case NULL -> null;
                case PAST -> OffsetDateTime.now().minusDays(1);
                case FUTURE -> OffsetDateTime.now().plusDays(1);
            };
        }
    }
}
