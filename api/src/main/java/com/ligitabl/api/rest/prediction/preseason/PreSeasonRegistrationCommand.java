package com.ligitabl.api.rest.prediction.preseason;

import java.util.List;
import java.util.Objects;

public record PreSeasonRegistrationCommand(List<SwapPair> swaps) {
    public PreSeasonRegistrationCommand {
        Objects.requireNonNull(swaps, "swaps must not be null");
    }

    public record SwapPair(String teamACode, String teamBCode) {}
}
