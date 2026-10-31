package com.ligitabl.model.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder(toBuilder = true)
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
