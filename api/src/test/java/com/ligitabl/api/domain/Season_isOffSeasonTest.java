package com.ligitabl.api.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.ligitabl.model.domain.Season;

class Season_isOffSeasonTest {

    @Test
    void notCompleted_alwaysFalse_regardlessOfPredictionsOpenAt() {
        assertThat(buildSeason(false, null).isOffSeason()).isFalse();
        assertThat(buildSeason(false, OffsetDateTime.now().plusDays(1)).isOffSeason())
                .isFalse();
        assertThat(buildSeason(false, OffsetDateTime.now().minusDays(1)).isOffSeason())
                .isFalse();
    }

    @Test
    void completedAndNullPredictionsOpenAt_returnsTrue() {
        // A finished legacy season with no pre-season config was never "predictions open" —
        // unlike isPredictionsOpen(), null here must NOT default to open.
        Season season = buildSeason(true, null);
        assertThat(season.isOffSeason()).isTrue();
    }

    @Test
    void completedAndPredictionsOpenAtInFuture_returnsTrue() {
        Season season = buildSeason(true, OffsetDateTime.now().plusDays(1));
        assertThat(season.isOffSeason()).isTrue();
    }

    @Test
    void completedAndPredictionsOpenAtInPast_returnsFalse() {
        Season season = buildSeason(true, OffsetDateTime.now().minusMinutes(1));
        assertThat(season.isOffSeason()).isFalse();
    }

    @Test
    void isOffSeasonAndIsPreSeason_neverBothTrue() {
        for (boolean completed : new boolean[] {true, false}) {
            for (OffsetDateTime predictionsOpenAt : new OffsetDateTime[] {
                null, OffsetDateTime.now().plusDays(1), OffsetDateTime.now().minusDays(1)
            }) {
                Season season = buildSeason(completed, predictionsOpenAt);
                assertThat(season.isOffSeason() && season.isPreSeason()).isFalse();
            }
        }
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
