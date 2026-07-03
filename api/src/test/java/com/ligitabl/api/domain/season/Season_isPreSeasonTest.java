package com.ligitabl.api.domain.season;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.ligitabl.model.domain.Season;

class Season_isPreSeasonTest {

    @Test
    void nullPreSeasonOpensAt_alwaysFalse_regardlessOfOtherFields() {
        // isPreSeasonOpen() requires a non-null preSeasonOpensAt — without it, isPreSeason() can
        // never be true, no matter what completed/predictionsOpenAt are.
        assertThat(buildSeason(false, null, null).isPreSeason()).isFalse();
        assertThat(buildSeason(false, null, OffsetDateTime.now().plusDays(1)).isPreSeason())
                .isFalse();
        assertThat(buildSeason(true, null, null).isPreSeason()).isFalse();
    }

    @Test
    void futurePreSeasonOpensAt_alwaysFalse_regardlessOfOtherFields() {
        // The pre-season window hasn't opened yet — isPreSeason() is false regardless of completed
        // or predictionsOpenAt.
        OffsetDateTime future = OffsetDateTime.now().plusDays(1);
        assertThat(buildSeason(false, future, null).isPreSeason()).isFalse();
        assertThat(buildSeason(false, future, OffsetDateTime.now().plusDays(30)).isPreSeason())
                .isFalse();
        assertThat(buildSeason(true, future, null).isPreSeason()).isFalse();
    }

    @Test
    void pastPreSeasonOpensAt_andPredictionsOpenAtInFuture_returnsTrue() {
        // Once preSeasonOpensAt has passed, isOffSeason() is false by construction (isBefore is
        // false), so isPreSeason() depends only on whether predictions have opened yet. This holds
        // regardless of `completed`, since isOffSeason() is already false here either way.
        OffsetDateTime past = OffsetDateTime.now().minusDays(1);
        OffsetDateTime future = OffsetDateTime.now().plusDays(30);
        assertThat(buildSeason(false, past, future).isPreSeason()).isTrue();
        assertThat(buildSeason(true, past, future).isPreSeason()).isTrue();
    }

    @Test
    void pastPreSeasonOpensAt_andNullPredictionsOpenAt_returnsFalse() {
        // Null predictionsOpenAt defaults to "predictions open" (back-compat), so pre-season is
        // already over.
        OffsetDateTime past = OffsetDateTime.now().minusDays(1);
        assertThat(buildSeason(false, past, null).isPreSeason()).isFalse();
        assertThat(buildSeason(true, past, null).isPreSeason()).isFalse();
    }

    @Test
    void pastPreSeasonOpensAt_andPastPredictionsOpenAt_returnsFalse() {
        // Predictions are already open — no longer pre-season.
        OffsetDateTime past = OffsetDateTime.now().minusDays(1);
        OffsetDateTime morePast = OffsetDateTime.now().minusMinutes(1);
        assertThat(buildSeason(false, past, morePast).isPreSeason()).isFalse();
        assertThat(buildSeason(true, past, morePast).isPreSeason()).isFalse();
    }

    private Season buildSeason(boolean completed, OffsetDateTime preSeasonOpensAt, OffsetDateTime predictionsOpenAt) {
        return Season.builder()
                .clientId(1)
                .competitionId(java.util.UUID.randomUUID())
                .name("Test Season")
                .slug(com.ligitabl.model.domain.SeasonSlug.of("2025-26"))
                .startDate(java.time.LocalDate.now())
                .endDate(java.time.LocalDate.now().plusMonths(9))
                .completed(completed)
                .preSeasonOpensAt(preSeasonOpensAt)
                .predictionsOpenAt(predictionsOpenAt)
                .build();
    }
}
