package com.ligitabl.model.query;

import java.time.OffsetDateTime;

/**
 * Min/max kickoff times for a single round position within a season.
 * Used to derive sprint start/end dates for display.
 */
public record RoundDateRange(int roundPosition, OffsetDateTime firstKickoff, OffsetDateTime lastKickoff) {}
