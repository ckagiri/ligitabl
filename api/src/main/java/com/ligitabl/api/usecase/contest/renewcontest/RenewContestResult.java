package com.ligitabl.api.usecase.contest.renewcontest;

import java.util.UUID;

public record RenewContestResult(UUID renewedContestId, String joinCode) {}
