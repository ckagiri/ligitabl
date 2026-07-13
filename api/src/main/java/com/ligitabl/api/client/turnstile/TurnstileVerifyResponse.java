package com.ligitabl.api.client.turnstile;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TurnstileVerifyResponse(
        boolean success,
        @JsonProperty("error-codes") List<String> errorCodes,
        @JsonProperty("challenge_ts") String challengeTs,
        String hostname,
        String action,
        String cdata) {}
