package com.ligitabl.model.domain;

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
