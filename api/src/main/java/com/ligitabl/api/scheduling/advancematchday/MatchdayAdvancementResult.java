package com.ligitabl.api.scheduling.advancematchday;

import java.util.UUID;

/**
 * Result of round advancement operation
 */
public record MatchdayAdvancementResult(
        UUID seasonId, int previousMatchday, int newMatchday, boolean advanced, String reason) {
    public static MatchdayAdvancementResult noChange(UUID seasonId, int matchday, String reason) {
        return new MatchdayAdvancementResult(seasonId, matchday, matchday, false, reason);
    }

    public static MatchdayAdvancementResult advanced(UUID seasonId, int previousMatchday, int newMatchday) {
        return new MatchdayAdvancementResult(
                seasonId,
                previousMatchday,
                newMatchday,
                true,
                "Matchday advanced from " + previousMatchday + " to " + newMatchday);
    }
}
