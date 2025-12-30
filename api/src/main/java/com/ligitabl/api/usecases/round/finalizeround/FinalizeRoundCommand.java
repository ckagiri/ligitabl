package com.ligitabl.api.usecases.round.finalizeround;

import java.util.UUID;

public record FinalizeRoundCommand (
    UUID seasonId
) {}
