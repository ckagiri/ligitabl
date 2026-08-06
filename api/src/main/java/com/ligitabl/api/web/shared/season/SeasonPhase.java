package com.ligitabl.api.web.shared.season;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import com.ligitabl.model.domain.Season;
import com.ligitabl.model.domain.SeasonState;

/**
 * A season's pre-season/off-season/in-play timing, resolved into the countdown fields used by
 * season-phase UI banners (predictions page, homepage).
 */
public record SeasonPhase(
        boolean isPreSeason,
        boolean isOffSeason,
        boolean isInPlay,
        boolean isInactive,
        Long daysToPreSeason,
        boolean preSeasonAboutToStart,
        Long daysToPredictions,
        boolean predictionsAboutToStart,
        OffsetDateTime predictionsOpenAt) {

    /**
     * @param at the instant to resolve against — {@code clock.instant()} in production. Taken as a
     *     parameter rather than read here so every field below is derived from one instant: a method
     *     that called the clock four times could report a countdown and a phase that disagree,
     *     across a midnight boundary.
     */
    public static SeasonPhase resolve(Season season, Instant at) {
        OffsetDateTime now = OffsetDateTime.ofInstant(at, ZoneOffset.UTC);

        Long daysToPreSeason = null;
        boolean preSeasonAboutToStart = false;
        if (season.getPreSeasonOpensAt() != null && !season.isPreSeasonOpen(at)) {
            long days = ChronoUnit.DAYS.between(now, season.getPreSeasonOpensAt());
            if (days >= 1) {
                daysToPreSeason = days;
            } else {
                preSeasonAboutToStart = true;
            }
        }

        Long daysToPredictions = null;
        boolean predictionsAboutToStart = false;
        OffsetDateTime predictionsOpenAt = null;
        if (season.isPreSeason(at) && season.getPredictionsOpenAt() != null) {
            long days = ChronoUnit.DAYS.between(now, season.getPredictionsOpenAt());
            if (days >= 1) {
                daysToPredictions = days;
            } else {
                predictionsAboutToStart = true;
            }
            predictionsOpenAt = season.getPredictionsOpenAt();
        }

        SeasonState state = season.getSeasonState(at);
        return new SeasonPhase(
                state == SeasonState.PRE_SEASON,
                state == SeasonState.OFF_SEASON,
                state == SeasonState.IN_PLAY,
                state == SeasonState.INACTIVE,
                daysToPreSeason,
                preSeasonAboutToStart,
                daysToPredictions,
                predictionsAboutToStart,
                predictionsOpenAt);
    }
}
