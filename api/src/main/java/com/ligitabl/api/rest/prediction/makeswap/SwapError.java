package com.ligitabl.api.rest.prediction.makeswap;

import java.time.Instant;
import java.util.UUID;

public sealed interface SwapError {
    record NoPredictionFound(UUID userId, UUID seasonId) implements SwapError {}

    record RoundNotFound(int roundPosition, UUID seasonId) implements SwapError {}

    record RoundNotOpen(String roundStatus) implements SwapError {}

    record CooldownActive(Instant nextSwapAt, double hoursRemaining) implements SwapError {}

    record InvalidTeamCode(String code) implements SwapError {}

    record TeamsNotFound(String teamACode, String teamBCode) implements SwapError {}

    record SeasonCompleted() implements SwapError {}

    record CurrentRoundNotFound(UUID seasonId) implements SwapError {}

    record UseOpeningWindowFirst(int round) implements SwapError {}
}
