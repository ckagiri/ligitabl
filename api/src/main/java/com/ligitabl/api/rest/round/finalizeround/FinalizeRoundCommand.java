package com.ligitabl.api.rest.round.finalizeround;

import java.util.UUID;

public record FinalizeRoundCommand(UUID seasonId, Integer roundPosition, boolean recompute) {
    public static FinalizeRoundCommand of(UUID seasonId) {
        return new FinalizeRoundCommand(seasonId, null, false);
    }

    public static FinalizeRoundCommand refinalize(UUID seasonId, int roundPosition) {
        return new FinalizeRoundCommand(seasonId, roundPosition, true);
    }
}
