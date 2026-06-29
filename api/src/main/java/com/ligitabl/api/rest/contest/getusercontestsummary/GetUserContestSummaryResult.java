package com.ligitabl.api.rest.contest.getusercontestsummary;

import java.util.List;

public record GetUserContestSummaryResult(
        List<GeneralContestRowDto> generalContests, List<PrivateContestRowDto> privateContests) {}
