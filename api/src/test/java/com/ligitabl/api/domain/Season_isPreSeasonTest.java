package com.ligitabl.api.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.ligitabl.model.domain.Season;

class Season_isPreSeasonTest {

    @Test
    void completedTrue_alwaysFalse_regardlessOfPredictionsOpenAt() {
        Season season = buildSeason(true, null);
        assertThat(season.isPreSeason()).isFalse();

        Season seasonWithFuture = buildSeason(true, OffsetDateTime.now().plusDays(1));
        assertThat(seasonWithFuture.isPreSeason()).isFalse();

        Season seasonWithPast = buildSeason(true, OffsetDateTime.now().minusDays(1));
        assertThat(seasonWithPast.isPreSeason()).isFalse();
    }

    @Test
    void notCompletedAndNullPredictionsOpenAt_returnsFalse() {
        // null predictionsOpenAt now defaults to "predictions open" (back-compat), so pre-season is false
        Season season = buildSeason(false, null);
        assertThat(season.isPreSeason()).isFalse();
    }

    @Test
    void notCompletedAndPredictionsOpenAtInFuture_returnsTrue() {
        Season season = buildSeason(false, OffsetDateTime.now().plusDays(1));
        assertThat(season.isPreSeason()).isTrue();
    }

    @Test
    void notCompletedAndPredictionsOpenAtInPast_returnsFalse() {
        Season season = buildSeason(false, OffsetDateTime.now().minusMinutes(1));
        assertThat(season.isPreSeason()).isFalse();
    }

    private Season buildSeason(boolean completed, OffsetDateTime predictionsOpenAt) {
        return Season.builder()
                .clientId(1)
                .competitionId(java.util.UUID.randomUUID())
                .name("Test Season")
                .slug(com.ligitabl.model.domain.SeasonSlug.of("2025-26"))
                .startDate(java.time.LocalDate.now())
                .endDate(java.time.LocalDate.now().plusMonths(9))
                .completed(completed)
                .predictionsOpenAt(predictionsOpenAt)
                .build();
    }
}
