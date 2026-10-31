package com.ligitabl.model.domain.prediction;

import java.time.Instant;

import com.ligitabl.model.domain.league.RoundStatus;

/**
 * Whether a prediction may change in a round right now. {@link SeasonPrediction#swapEligibility}
 * answers for a single swap, {@link SeasonPrediction#openingSwapEligibility} for the opening window.
 */
public sealed interface SwapEligibility {

    sealed interface Allowed extends SwapEligibility {}

    sealed interface Blocked extends SwapEligibility {}

    record FirstSwapFree() implements Allowed {}

    record CooldownElapsed() implements Allowed {}

    record OpeningWindowOpen() implements Allowed {}

    record RoundNotOpen(RoundStatus status) implements Blocked {}

    record PreSeasonMergeRequired() implements Blocked {}

    record UseOpeningWindowFirst() implements Blocked {}

    record OpeningAlreadyUsed() implements Blocked {}

    record CooldownActive(Instant nextSwapAt) implements Blocked {}

    default boolean allowed() {
        return this instanceof Allowed;
    }
}
