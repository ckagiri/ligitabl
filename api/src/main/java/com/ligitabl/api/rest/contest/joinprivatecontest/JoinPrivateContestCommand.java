package com.ligitabl.api.rest.contest.joinprivatecontest;

import java.util.UUID;

public record JoinPrivateContestCommand(UUID userId, String joinCode) {}
