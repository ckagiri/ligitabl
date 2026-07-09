package com.ligitabl.seed.internal;

/**
 * The competition/season pair resolved as "current" for this seed run, via
 * {@link CurrentSeasonResolver}. Distinct from {@link DefaultsConfig}, which only carries
 * what defaults.yaml actually declares (competitionSlug) — this is the seeder-facing,
 * fully-resolved output.
 */
public record CurrentSeason(String competitionSlug, String seasonSlug) {}
