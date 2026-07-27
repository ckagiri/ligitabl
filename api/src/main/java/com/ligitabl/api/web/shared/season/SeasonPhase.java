package com.ligitabl.api.web.shared.season;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import com.ligitabl.model.domain.Season;

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

    public static SeasonPhase resolve(Season season) {
        Long daysToPreSeason = null;
        boolean preSeasonAboutToStart = false;
        if (season.getPreSeasonOpensAt() != null && !season.isPreSeasonOpen()) {
            long days = ChronoUnit.DAYS.between(OffsetDateTime.now(), season.getPreSeasonOpensAt());
            if (days >= 1) {
                daysToPreSeason = days;
            } else {
                preSeasonAboutToStart = true;
            }
        }

        Long daysToPredictions = null;
        boolean predictionsAboutToStart = false;
        OffsetDateTime predictionsOpenAt = null;
        if (season.isPreSeason() && season.getPredictionsOpenAt() != null) {
            long days = ChronoUnit.DAYS.between(OffsetDateTime.now(), season.getPredictionsOpenAt());
            if (days >= 1) {
                daysToPredictions = days;
            } else {
                predictionsAboutToStart = true;
            }
            predictionsOpenAt = season.getPredictionsOpenAt();
        }

        return new SeasonPhase(
                season.isPreSeason(),
                season.isOffSeason(),
                season.isInPlay(),
                season.isInactive(),
                daysToPreSeason,
                preSeasonAboutToStart,
                daysToPredictions,
                predictionsAboutToStart,
                predictionsOpenAt);
    }
}
