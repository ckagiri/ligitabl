package com.ligitabl.api.domain.season;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import com.ligitabl.model.domain.Season;

class Season_isOffSeasonTest {

    @Test
    void notCompleted_alwaysFalse_regardlessOfPreSeasonOpensAt() {
        assertThat(buildSeason(false, null).isOffSeason()).isFalse();
        assertThat(buildSeason(false, OffsetDateTime.now().plusDays(1)).isOffSeason())
                .isFalse();
        assertThat(buildSeason(false, OffsetDateTime.now().minusDays(1)).isOffSeason())
                .isFalse();
    }

    @Test
    void completedAndNullPreSeasonOpensAt_returnsTrue() {
        // A finished legacy season with no pre-season config was never "pre-season open" —
        // unlike isPredictionsOpen(), null here must NOT default to open.
        Season season = buildSeason(true, null);
        assertThat(season.isOffSeason()).isTrue();
    }

    @Test
    void completedAndPreSeasonOpensAtInFuture_returnsTrue() {
        Season season = buildSeason(true, OffsetDateTime.now().plusDays(1));
        assertThat(season.isOffSeason()).isTrue();
    }

    @Test
    void completedAndPreSeasonOpensAtInPast_returnsFalse() {
        Season season = buildSeason(true, OffsetDateTime.now().minusMinutes(1));
        assertThat(season.isOffSeason()).isFalse();
    }

    @Test
    void isOffSeasonAndIsPreSeason_neverBothTrue() {
        for (boolean completed : new boolean[] {true, false}) {
            for (OffsetDateTime preSeasonOpensAt : new OffsetDateTime[] {
                null, OffsetDateTime.now().plusDays(1), OffsetDateTime.now().minusDays(1)
            }) {
                for (OffsetDateTime predictionsOpenAt : new OffsetDateTime[] {
                    null, OffsetDateTime.now().plusDays(1), OffsetDateTime.now().minusDays(1)
                }) {
                    Season season = buildSeason(completed, preSeasonOpensAt, predictionsOpenAt);
                    assertThat(season.isOffSeason() && season.isPreSeason()).isFalse();
                }
            }
        }
    }

    @Test
    void isOffSeasonAndIsPredictionsOpen_canBothBeTrue_backwardCompat() {
        // A completed season with no preSeasonOpensAt configured is off-season forever (by design),
        // but a null predictionsOpenAt still defaults to "predictions open" — these are allowed to
        // overlap; no mutual-exclusion guard exists between isOffSeason() and isPredictionsOpen().
        Season season = buildSeason(true, null, null);
        assertThat(season.isOffSeason()).isTrue();
        assertThat(season.isPredictionsOpen()).isTrue();
    }

    private Season buildSeason(boolean completed, OffsetDateTime preSeasonOpensAt) {
        return buildSeason(completed, preSeasonOpensAt, null);
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
