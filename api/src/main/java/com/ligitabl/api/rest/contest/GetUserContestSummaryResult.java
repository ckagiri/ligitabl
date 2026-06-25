package com.ligitabl.api.rest.contest;

import java.util.List;

public record GetUserContestSummaryResult(
        List<GeneralContestRowDto> generalContests, List<PrivateContestRowDto> privateContests) {}
