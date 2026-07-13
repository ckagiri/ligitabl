package com.ligitabl.model.domain;

/**
 * UI-2 tiering from the suspicious-email-detector spec: 🚩 {@link #FLAG} = suspicious email AND no
 * genuine engagement (high-confidence spam); ⚠️ {@link #CAUTION} = suspicious email but the account
 * shows real activity (worth a glance, possibly a false positive); {@link #NONE} = not suspicious.
 * {@code eligibleForDelete} is the same signal driving the admin list's delete button (see
 * {@code EngagementInfo.eligibleForDelete()}) — reused here rather than threading a separate
 * "hasActivity" boolean, so both flags key off one definition of "no engagement."
 */
public enum SuspicionTier {
    NONE,
    CAUTION,
    FLAG;

    public static SuspicionTier of(SuspiciousEmailDetector.Result result, boolean eligibleForDelete) {
        if (!result.isSuspicious()) {
            return NONE;
        }
        return eligibleForDelete ? FLAG : CAUTION;
    }
}
