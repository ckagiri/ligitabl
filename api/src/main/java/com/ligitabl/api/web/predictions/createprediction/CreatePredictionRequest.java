package com.ligitabl.api.web.predictions.createprediction;

import java.util.List;
import java.util.Objects;

/**
 * A first season prediction: 1-5 swaps applied to the season's initial rankings.
 *
 * <p>Example JSON: {@code { "swaps": [{"teamACode": "MCI", "teamBCode": "ARS"}] }}</p>
 */
public record CreatePredictionRequest(List<SwapPair> swaps) {
    public CreatePredictionRequest {
        Objects.requireNonNull(swaps, "swaps is required");
    }

    public record SwapPair(String teamACode, String teamBCode) {}
}
