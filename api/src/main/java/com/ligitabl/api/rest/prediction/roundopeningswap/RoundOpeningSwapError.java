package com.ligitabl.api.rest.prediction.roundopeningswap;

import java.util.UUID;

public sealed interface RoundOpeningSwapError {
    record NoPredictionFound(UUID userId, UUID seasonId) implements RoundOpeningSwapError {}

    record CurrentRoundNotFound(UUID seasonId) implements RoundOpeningSwapError {}

    record RoundNotOpen(String roundStatus) implements RoundOpeningSwapError {}

    record OpeningAlreadyUsed(int round) implements RoundOpeningSwapError {}

    record BatchSizeInvalid(int size) implements RoundOpeningSwapError {}

    record InvalidTeamCode(String code) implements RoundOpeningSwapError {}

    record TeamsNotFound(String teamACode, String teamBCode) implements RoundOpeningSwapError {}

    record SeasonCompleted() implements RoundOpeningSwapError {}
}
