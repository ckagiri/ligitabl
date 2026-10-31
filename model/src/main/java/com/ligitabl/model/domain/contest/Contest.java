package com.ligitabl.model.domain.contest;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.ligitabl.model.domain.AbstractModel;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder(toBuilder = true)
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

    public void rename(String newName) {
        name = newName;
    }

    public void replaceJoinCode(String newCode) {
        joinCode = newCode;
    }

    public void toggleJoining() {
        isOpen = !isOpen;
    }

    public void markRenewedInto(UUID contestId) {
        renewedIntoContestId = contestId;
    }
}
