package com.ligitabl.api.usecases.season.seasonsetupmode;

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
    UUID previousMainContestId;
    String message;
    Instant timestamp;
}
