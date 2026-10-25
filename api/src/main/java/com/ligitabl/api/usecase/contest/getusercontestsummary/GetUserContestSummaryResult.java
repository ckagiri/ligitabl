package com.ligitabl.api.usecase.contest.getusercontestsummary;

import java.util.List;

public record GetUserContestSummaryResult(
        List<GeneralContestRowDto> generalContests, List<PrivateContestRowDto> privateContests) {}
