package com.ligitabl.api.usecase.contest.renewcontest;

import java.util.UUID;

public record RenewContestCommand(UUID userId, UUID contestId, String toSprintCode) {}
