package com.ligitabl.api.rest.season.seasonsetupmode;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SetupModeResult {
    UUID seasonId;
    String seasonSlug;
    boolean isInSetupMode;
    UUID mainContestId;
    UUID detachedContestId;
    String message;
    Instant timestamp;
}
