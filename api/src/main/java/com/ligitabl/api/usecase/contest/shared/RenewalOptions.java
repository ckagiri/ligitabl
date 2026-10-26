package com.ligitabl.api.usecase.contest.shared;

import java.util.List;

/** The renew control on a contest row: whether to show it, and the sprint codes it offers. */
public record RenewalOptions(
        boolean visible, boolean enabled, String fromCode, String defaultToCode, List<String> toOptionCodes) {

    public static RenewalOptions hidden() {
        return new RenewalOptions(false, false, null, null, List.of());
    }
}
