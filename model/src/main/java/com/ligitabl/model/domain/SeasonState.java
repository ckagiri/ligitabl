package com.ligitabl.model.domain;

/**
 * Coarse-grained season phase, derived from {@link Season#getSeasonState()}.
 */
public enum SeasonState {
    OFF_SEASON,
    PRE_SEASON,
    IN_PLAY,
    INACTIVE
}
