package com.ligitabl.api.rest.contest.renewcontest;

import java.util.UUID;

public record RenewContestCommand(UUID userId, UUID contestId, String toSprintCode) {}
