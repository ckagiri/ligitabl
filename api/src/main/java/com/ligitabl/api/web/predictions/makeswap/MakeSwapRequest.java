package com.ligitabl.api.web.predictions.makeswap;

import java.util.Objects;

/**
 * Swaps exactly two teams; positions are resolved server-side.
 *
 * <p>Example JSON: {@code { "teamACode": "MCI", "teamBCode": "ARS" }}</p>
 */
public record MakeSwapRequest(String teamACode, String teamBCode) {
    public MakeSwapRequest(String teamACode, String teamBCode) {
        this.teamACode = Objects.requireNonNull(teamACode, "teamACode is required");
        this.teamBCode = Objects.requireNonNull(teamBCode, "teamBCode is required");
    }
}
