package com.ligitabl.api.rest.contest;

import java.util.UUID;

public record GetUserContestSummaryQuery(UUID userId, String competitionSlug) {}
