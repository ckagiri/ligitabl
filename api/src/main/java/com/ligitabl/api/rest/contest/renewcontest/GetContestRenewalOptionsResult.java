package com.ligitabl.api.rest.contest.renewcontest;

import java.util.List;

public record GetContestRenewalOptionsResult(
        boolean isRenewable,
        String contestName,
        String fromCode,
        String defaultToCode,
        List<String> toOptionCodes,
        int activeMemberCount) {

    public static GetContestRenewalOptionsResult notRenewable() {
        return new GetContestRenewalOptionsResult(false, null, null, null, List.of(), 0);
    }
}
