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

    // Populated by the database (default + trigger); never set explicitly on insert/update.
    private OffsetDateTime createDate;

    private OffsetDateTime updateDate;

    private UUID ownerId;

    private boolean isOpen;

    private UUID renewedIntoContestId;

    public boolean isMain() {
        return !isPrivate && fromRoundPosition == 1;
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerId != null && ownerId.equals(userId);
    }
}
