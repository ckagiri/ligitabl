package com.ligitabl.api.rest.contest;

import java.util.UUID;

public record JoinPrivateContestCommand(UUID userId, String joinCode) {}
