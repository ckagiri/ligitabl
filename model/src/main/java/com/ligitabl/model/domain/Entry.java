package com.ligitabl.model.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Value
@Builder
@Jacksonized
public class Entry extends AbstractModel<UUID> {
    @NotNull
    UUID userId;

    @NotNull
    UUID contestId;

    Instant joinedAt;
}
