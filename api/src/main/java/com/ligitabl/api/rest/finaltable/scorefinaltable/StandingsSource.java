package com.ligitabl.api.rest.finaltable.scorefinaltable;

/**
 * Which standings a scoring pass measures predictions against.
 *
 * <p>Deliberately has no default. A source that can be defaulted wrong is a source that will
 * eventually write provisional scores into a completed season, so every caller states its intent.
 */
public enum StandingsSource {

    /** The real path: the standings for the season's last round. */
    FINAL_ROUND,

    /**
     * The latest standings available, whatever round they belong to. Dev preview only — the numbers
     * are meaningless mid-season, but the rendering is exactly what the end of the season produces.
     */
    CURRENT
}
