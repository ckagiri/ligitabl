package com.ligitabl.api.rest.contest.renewcontest;

import java.util.List;

public record GetContestRenewalOptionsResult(
        boolean visible,
        boolean enabled,
        String contestName,
        String fromCode,
        String defaultToCode,
        List<String> toOptionCodes,
        int activeMemberCount) {

    public static GetContestRenewalOptionsResult hidden() {
        return new GetContestRenewalOptionsResult(false, false, null, null, null, List.of(), 0);
    }
}
