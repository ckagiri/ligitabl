package com.ligitabl.api.web.prediction.makeswap;

import java.util.Objects;

/**
 * Request DTO for swapping exactly two teams.
 *
 * <p>Used by existing participants to update their prediction.
 * Only team codes are required — current positions are resolved server-side.</p>
 *
 * <p>Example JSON: {@code { "teamACode": "MCI", "teamBCode": "ARS" }}</p>
 */
public record MakeSwapRequest(String teamACode, String teamBCode) {
    public MakeSwapRequest(String teamACode, String teamBCode) {
        this.teamACode = Objects.requireNonNull(teamACode, "teamACode is required");
        this.teamBCode = Objects.requireNonNull(teamBCode, "teamBCode is required");
    }
}

