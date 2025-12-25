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
public class Contest extends AbstractModel<UUID> {
    @NotNull
    private UUID seasonId;

    @NotNull
    private String name;

    private boolean isPrivate;

    private String joinCode;

    private int fromRoundPosition;

    private int toRoundPosition;

    private Integer maxEntries;

    private Instant createdAt;

    public boolean isDefault() {
        return !isPrivate && fromRoundPosition == 1;
    }
}
