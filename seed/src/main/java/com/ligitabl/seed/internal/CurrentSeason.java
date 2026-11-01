package com.ligitabl.seed.internal;

/**
 * The competition/season pair resolved as "current" for this seed run by {@link CurrentSeasonResolver}.
 * {@link DefaultsConfig} carries only what defaults.yaml declares.
 */
public record CurrentSeason(String competitionSlug, String seasonSlug) {}
