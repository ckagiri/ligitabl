package com.ligitabl.api.web.shared.domain.prediction;

/**
 * Represents the access mode for viewing/editing predictions.
 * Determines what UI controls and messages are shown.
 */
public enum PredictionAccessMode {
    /**
     * User owns prediction and can modify (cooldown allows).
     */
    EDITABLE,

    /**
     * Guest viewing fallback - must login to modify.
     */
    READONLY_GUEST,

    /**
     * User on cooldown - temporarily readonly.
     */
    READONLY_COOLDOWN,

    /**
     * Viewing another user's prediction (readonly).
     */
    READONLY_VIEWING_OTHER,

    /**
     * Viewing non-existent user (readonly with error message).
     */
    READONLY_USER_NOT_FOUND,

    /**
     * Authenticated but no entry yet - can submit to join.
     */
    CAN_CREATE_ENTRY;

    /**
     * Check if this mode is readonly.
     */
    public boolean isReadonly() {
        return this != EDITABLE && this != CAN_CREATE_ENTRY;
    }

    /**
     * Check if user can create an entry (initial prediction).
     */
    public boolean canCreateEntry() {
        return this == CAN_CREATE_ENTRY;
    }
}
