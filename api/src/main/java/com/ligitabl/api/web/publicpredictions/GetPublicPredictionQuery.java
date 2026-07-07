package com.ligitabl.api.web.publicpredictions;

import java.util.Objects;
import java.util.UUID;

/**
 * Command for the public, no-login prediction view: a target user's publicId, the season being
 * viewed, and an optional requested round (clamped by the use case to that user's valid range).
 */
public record GetPublicPredictionQuery(String publicId, UUID seasonId, Integer requestedRound) {
    public GetPublicPredictionQuery {
        Objects.requireNonNull(publicId, "publicId is required");
        Objects.requireNonNull(seasonId, "seasonId is required");
    }
}
