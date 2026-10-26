package com.ligitabl.api.usecase.contest.renewcontest;

import java.util.List;

import com.ligitabl.api.usecase.contest.shared.RenewalOptions;

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

    public RenewalOptions options() {
        return new RenewalOptions(visible, enabled, fromCode, defaultToCode, toOptionCodes);
    }
}
