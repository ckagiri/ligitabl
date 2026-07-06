package com.ligitabl.api.rest.contest.renewcontest;

import java.util.UUID;

public record RenewContestResult(UUID renewedContestId, String joinCode) {}
