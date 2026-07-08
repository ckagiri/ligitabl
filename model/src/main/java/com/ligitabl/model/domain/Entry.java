package com.ligitabl.model.domain;

import java.time.OffsetDateTime;
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

    @NotNull
    Integer joinedAtRound;

    Integer removedAtRound;

    OffsetDateTime joinedAt;

    OffsetDateTime removedAt;
}
