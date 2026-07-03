package com.ligitabl.api.rest.prediction.shared;

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
     * Readonly — guest viewing fallback, user on cooldown, or a historical round.
     */
    READONLY,

    /**
     * Authenticated but no entry yet - can submit to join.
     */
    CAN_CREATE_ENTRY;

    /**
     * Check if this mode allows modifications.
     */
    public boolean isEditable() {
        return this == EDITABLE;
    }

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
