package com.ligitabl.api.web.predictions.createprediction;

import java.util.List;
import java.util.Objects;

/**
 * Request DTO for creating initial season prediction.
 *
 * <p>Used when a user first submits their season prediction (joins the competition).
 * Contains 1–3 swap pairs applied to the season's initial rankings — everyone
 * starts from the same baseline and submits between one and three swaps.</p>
 *
 * <p>Example JSON: {@code { "swaps": [{"teamACode": "MCI", "teamBCode": "ARS"}] }}</p>
 */
public record CreatePredictionRequest(List<SwapPair> swaps) {
    public CreatePredictionRequest {
        Objects.requireNonNull(swaps, "swaps is required");
    }

    public record SwapPair(String teamACode, String teamBCode) {}
}
