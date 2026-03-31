package com.ligitabl.api.rest.standings;

/**
 * One match result from a team's perspective, used to render the Form column.
 *
 * @param result       "W", "D", or "L"
 * @param wasHome      true if the team played at home
 * @param opponentCode opponent's TLA (e.g. "ARS")
 * @param goalsFor     goals scored by the team in this match
 * @param goalsAgainst goals scored by the opponent in this match
 */
public record FormEntry(String result, boolean wasHome, String opponentCode, int goalsFor, int goalsAgainst) {}
