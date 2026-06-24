package com.ligitabl.api.rest.prediction.createprediction;

import java.util.List;
import java.util.Objects;

public record CreatePredictionCommand(List<SwapPair> swaps) {
    public CreatePredictionCommand {
        Objects.requireNonNull(swaps, "swaps must not be null");
    }

    public record SwapPair(String teamACode, String teamBCode) {}
}
