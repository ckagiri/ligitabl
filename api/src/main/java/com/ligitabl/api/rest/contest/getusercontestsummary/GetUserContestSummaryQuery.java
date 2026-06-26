package com.ligitabl.api.rest.contest.getusercontestsummary;

import java.util.UUID;

public record GetUserContestSummaryQuery(UUID userId, String competitionSlug) {}
