package com.ligitabl.api.usecases.team.getteambyid;

import com.ligitabl.model.validator.ValidUUID;

import java.util.UUID;

public record GetTeamByIdQuery(@ValidUUID String id) {
    public UUID getUuid() {
        return UUID.fromString(id);
    }
}
