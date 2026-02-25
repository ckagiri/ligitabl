package com.ligitabl.api.web.predictions.createprediction;

import java.util.Objects;

/**
 * Request DTO for creating initial season prediction.
 *
 * <p>Used when a user first submits their season prediction (joins the competition).
 * Contains the two team codes to swap from the season's initial rankings — everyone
 * starts from the same baseline and submits exactly one swap.</p>
 *
 * <p>Example JSON: {@code { "teamACode": "MCI", "teamBCode": "ARS" }}</p>
 */
public record CreatePredictionRequest(String teamACode, String teamBCode) {
    public CreatePredictionRequest {
        Objects.requireNonNull(teamACode, "teamACode is required");
        Objects.requireNonNull(teamBCode, "teamBCode is required");
    }
}
