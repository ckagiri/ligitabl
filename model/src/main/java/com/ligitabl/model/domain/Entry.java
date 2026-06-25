package com.ligitabl.model.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class Entry extends AbstractModel<UUID> {
    @NotNull
    UUID userId;

    @NotNull
    UUID contestId;

    Instant joinedAt;

    int joinedAtRound;

    Instant removedAt;

    Integer removedAtRound;
}
