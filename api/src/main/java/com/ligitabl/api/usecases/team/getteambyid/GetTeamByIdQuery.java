package com.ligitabl.api.usecases.team.getteambyid;

import java.util.UUID;

import com.ligitabl.model.validator.ValidUUID;

public record GetTeamByIdQuery(@ValidUUID String id) {
    public UUID getUuid() {
        return UUID.fromString(id);
    }
}
