package com.ligitabl.api.usecase.standings.shared;

/**
 * One match result from a team's perspective, for the Form column.
 *
 * @param result "W", "D", or "L"
 * @param opponentCode opponent's TLA (e.g. "ARS")
 */
public record FormEntry(String result, boolean wasHome, String opponentCode, int goalsFor, int goalsAgainst) {}
