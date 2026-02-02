package com.ligitabl.api.rest.team.deleteteam;

import java.util.UUID;

import com.ligitabl.model.validator.ValidUUID;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DeleteTeamCommand {
    @ValidUUID
    private String id;

    public UUID getUuid() {
        return UUID.fromString(id);
    }

    public static DeleteTeamCommand of(String id) {
        return new DeleteTeamCommand(id);
    }
}
