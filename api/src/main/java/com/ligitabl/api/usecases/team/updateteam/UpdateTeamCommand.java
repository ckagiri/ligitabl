package com.ligitabl.api.usecases.team.updateteam;

import com.ligitabl.api.usecases.team.TeamPayload;
import com.ligitabl.model.validator.ValidUUID;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
public class UpdateTeamCommand extends TeamPayload {
    @ValidUUID
    private String id;

    public UUID getUuid() {
        return UUID.fromString(id);
    }

    public static UpdateTeamCommand of(String id, TeamPayload data) {
        return UpdateTeamCommand.builder()
            .id(id)
            .name(data.getName())
            .shortName(data.getShortName())
            .slug(data.getSlug())
            .tla(data.getTla())
            .build();
    }
}

