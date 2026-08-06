package com.ligitabl.api.web.shared.season;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonSlug;

/**
 * {@link SeasonPhase} drives the season-phase banners on the homepage and the predictions page —
 * which phase is shown, and the "opens in N days" countdowns — and had no test of any kind.
 *
 * <p>It was written when the class was given an explicit instant to resolve against. The gap was
 * found by mutation: shifting that instant 400 days into the future changed nothing anywhere in the
 * suite, so every countdown and every phase flag this produces was unverified.
 */
@DisplayName("SeasonPhase.resolve")
class SeasonPhaseTest {

    private static final Instant NOW = Instant.parse("2026-07-23T12:00:00Z");
    private static final OffsetDateTime AT = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @Test
    void preSeasonOpen_predictionsStillAhead_isPreSeasonWithACountdown() {
        SeasonPhase phase = SeasonPhase.resolve(
                season(AT.minusDays(3), AT.plusDays(20), AT.toLocalDate().plusMonths(1)), NOW);

        assertThat(phase.isPreSeason()).isTrue();
        assertThat(phase.isInPlay()).isFalse();
        assertThat(phase.isOffSeason()).isFalse();
        assertThat(phase.isInactive()).isFalse();

        assertThat(phase.daysToPredictions()).isEqualTo(20L);
        assertThat(phase.predictionsAboutToStart()).isFalse();
        assertThat(phase.predictionsOpenAt()).isEqualTo(AT.plusDays(20));

        assertThat(phase.daysToPreSeason())
                .as("pre-season has already opened, so there is nothing to count down to")
                .isNull();
    }

    @Test
    void predictionsOpen_isInPlayWithNoCountdowns() {
        SeasonPhase phase = SeasonPhase.resolve(
                season(AT.minusDays(30), AT.minusDays(7), AT.toLocalDate().minusDays(1)), NOW);

        assertThat(phase.isInPlay()).isTrue();
        assertThat(phase.isPreSeason()).isFalse();
        assertThat(phase.daysToPredictions()).isNull();
        assertThat(phase.predictionsOpenAt())
                .as("only carried while the countdown is live — the banner has nothing to say once open")
                .isNull();
    }

    @Test
    void preSeasonStillAhead_countsDownToIt() {
        SeasonPhase phase = SeasonPhase.resolve(
                season(AT.plusDays(9), AT.plusDays(40), AT.toLocalDate().plusMonths(2)), NOW);

        assertThat(phase.daysToPreSeason()).isEqualTo(9L);
        assertThat(phase.preSeasonAboutToStart()).isFalse();
    }

    /**
     * Under a day is reported as "about to start" rather than "in 0 days" — the countdown uses whole
     * days, so the final stretch has to be a flag instead of a number.
     */
    @Test
    void underOneDayAway_flipsToAboutToStart() {
        SeasonPhase preSeasonImminent = SeasonPhase.resolve(
                season(AT.plusHours(5), AT.plusDays(40), AT.toLocalDate().plusMonths(2)), NOW);
        assertThat(preSeasonImminent.daysToPreSeason()).isNull();
        assertThat(preSeasonImminent.preSeasonAboutToStart()).isTrue();

        SeasonPhase predictionsImminent = SeasonPhase.resolve(
                season(AT.minusDays(3), AT.plusHours(5), AT.toLocalDate().plusMonths(1)), NOW);
        assertThat(predictionsImminent.daysToPredictions()).isNull();
        assertThat(predictionsImminent.predictionsAboutToStart()).isTrue();
    }

    /**
     * The reason {@code resolve} takes an instant rather than reading the clock six times: every
     * field has to describe the same moment. Resolving the same season a day apart must move the
     * countdown by exactly one day — and, at the boundary, must move the phase itself.
     */
    @Test
    void everyFieldIsDerivedFromTheOneInstantPassedIn() {
        Season season =
                season(AT.minusDays(3), AT.plusDays(20), AT.toLocalDate().plusMonths(1));

        assertThat(SeasonPhase.resolve(season, NOW).daysToPredictions()).isEqualTo(20L);
        assertThat(SeasonPhase.resolve(season, NOW.plusSeconds(86_400)).daysToPredictions())
                .isEqualTo(19L);

        // Twenty days on, the same season has crossed into play — phase and countdown move together.
        SeasonPhase later = SeasonPhase.resolve(season, NOW.plusSeconds(21L * 86_400));
        assertThat(later.isInPlay()).isTrue();
        assertThat(later.isPreSeason()).isFalse();
        assertThat(later.daysToPredictions()).isNull();
    }

    @Test
    void noPreSeasonDateConfigured_yieldsNoPreSeasonCountdown() {
        Season season = season(null, AT.plusDays(20), AT.toLocalDate().plusMonths(1));

        SeasonPhase phase = SeasonPhase.resolve(season, NOW);

        assertThat(phase.daysToPreSeason()).isNull();
        assertThat(phase.preSeasonAboutToStart()).isFalse();
        assertThat(phase.isPreSeason()).isFalse();
    }

    private Season season(OffsetDateTime preSeasonOpensAt, OffsetDateTime predictionsOpenAt, LocalDate startDate) {
        return Season.builder()
                .clientId(1)
                .competitionId(UUID.randomUUID())
                .name("Test Season")
                .slug(SeasonSlug.of("2026-27"))
                .startDate(startDate)
                .endDate(startDate.plusMonths(9))
                .completed(false)
                .preSeasonOpensAt(preSeasonOpensAt)
                .predictionsOpenAt(predictionsOpenAt)
                .build();
    }
}
