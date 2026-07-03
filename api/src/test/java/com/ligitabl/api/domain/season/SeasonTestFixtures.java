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
        return Season.builder()
                .clientId(1)
                .competitionId(UUID.randomUUID())
                .name("Test Season")
                .slug(SeasonSlug.of("2025-26"))
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusMonths(9))
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
