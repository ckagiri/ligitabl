package com.ligitabl.api.rest.finaltable.scorefinaltable;

/**
 * Which standings a scoring pass measures predictions against.
 */
public enum StandingsSource {

    /** The real path: the standings for the season's last round. */
    FINAL_ROUND,

    /**
     * The latest standings available, whatever round they belong to. Dev preview only.
     */
    CURRENT
}
