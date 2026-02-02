package com.ligitabl.api.rest.team.updateteam;

import java.util.UUID;

import com.ligitabl.api.rest.team.TeamPayload;
import com.ligitabl.model.validator.ValidUUID;

import lombok.*;
import lombok.experimental.SuperBuilder;

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
