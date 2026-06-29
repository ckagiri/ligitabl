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

    private UUID ownerId;

    private boolean isOpen;

    public boolean isMain() {
        return !isPrivate && fromRoundPosition == 1;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId != null && ownerId.equals(userId);
    }
}
