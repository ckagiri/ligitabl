package com.ligitabl.api.web.prediction.makeswap;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public record SwapTeamsRequest(
        @JsonProperty("teamACode") String teamACode,
        @JsonProperty("teamBCode") String teamBCode
) {
    @JsonCreator
    public SwapTeamsRequest(
            @JsonProperty("teamACode") String teamACode,
            @JsonProperty("teamBCode") String teamBCode
    ) {
        this.teamACode = Objects.requireNonNull(teamACode, "teamACode is required");
        this.teamBCode = Objects.requireNonNull(teamBCode, "teamBCode is required");
    }
}

