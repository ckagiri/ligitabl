package com.ligitabl.api.rest.contest;

import java.util.UUID;

public record CreatePrivateContestResult(UUID contestId, String joinCode) {}
