package com.ligitabl.api.rest.round.finalizeround;

import java.util.UUID;

public record FinalizeRoundCommand(UUID seasonId, boolean recompute) {
    public static FinalizeRoundCommand of(UUID seasonId) {
        return new FinalizeRoundCommand(seasonId, false);
    }
}
