package com.ligitabl.api.usecase.contest.createprivatecontest;

import java.util.UUID;

public record CreatePrivateContestResult(UUID contestId, String joinCode) {}
