package com.ligitabl.api.usecase.contest.getprofilecontestlists;

import java.util.List;

public record GetProfileContestListsResult(
        List<ContestSummary> activeContests,
        int activeTotal,
        int activePages,
        int activeFrom,
        int activeTo,
        List<ContestSummary> pastContests,
        int pastTotal,
        int pastPages,
        int pastFrom,
        int pastTo) {}
