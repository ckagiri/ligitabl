package com.ligitabl.api.rest.contest;

import java.util.UUID;

public record PrivateContestRowDto(
        UUID id, String name, String currentPhaseLabel, int memberCount, boolean isOwner) {}
