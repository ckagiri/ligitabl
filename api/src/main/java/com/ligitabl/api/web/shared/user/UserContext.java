package com.ligitabl.api.web.shared.user;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents the context of a user viewing predictions.
 * Encapsulates user resolution logic and state.
 */
public record UserContext(UUID userId, UserType userType, boolean hasMainContestEntry) {
    /**
     * Types of users in the prediction context.
     */
    public enum UserType {
        AUTHENTICATED, // Logged-in user viewing own predictions
        GUEST // Not logged in
    }

    public UserContext {
        // userId can be null for GUEST
        Objects.requireNonNull(userType, "userType is required");
    }

    public boolean isGuest() {
        return userType == UserType.GUEST;
    }

    public boolean isAuthenticated() {
        return userType == UserType.AUTHENTICATED;
    }

    /**
     * Create context for a guest user (not logged in).
     */
    public static UserContext guest() {
        return new UserContext(null, UserType.GUEST, false);
    }

    /**
     * Create context for an authenticated user viewing their own predictions.
     */
    public static UserContext authenticated(UUID userId, boolean hasMainContestEntry) {
        Objects.requireNonNull(userId, "userId is required for authenticated user");
        return new UserContext(userId, UserType.AUTHENTICATED, hasMainContestEntry);
    }
}
