package com.ligitabl.api.usecase.contest.getusercontestsummary;

import java.util.UUID;

public record GetUserContestSummaryQuery(UUID userId, String competitionSlug) {}
