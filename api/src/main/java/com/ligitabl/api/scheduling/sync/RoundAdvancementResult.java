package com.ligitabl.api.scheduling.sync;

import java.util.UUID;

/**
 * Result of round advancement operation
 */
public record RoundAdvancementResult(
        UUID seasonId, int previousMatchday, int newMatchday, boolean advanced, String reason) {
    public static RoundAdvancementResult noChange(UUID seasonId, int matchday, String reason) {
        return new RoundAdvancementResult(seasonId, matchday, matchday, false, reason);
    }

    public static RoundAdvancementResult advanced(UUID seasonId, int previousMatchday, int newMatchday) {
        return new RoundAdvancementResult(
                seasonId,
                previousMatchday,
                newMatchday,
                true,
                "Matchday advanced from " + previousMatchday + " to " + newMatchday);
    }
}
