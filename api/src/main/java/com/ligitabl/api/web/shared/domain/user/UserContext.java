package com.ligitabl.api.web.shared.domain.user;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents the context of a user viewing predictions.
 * Encapsulates user resolution logic and state.
 */
public record UserContext(UUID userId, UserType userType, boolean hasContestEntry) {
    /**
     * Types of users in the prediction context.
     */
    public enum UserType {
        AUTHENTICATED, // Logged in user viewing own predictions
        GUEST, // Not logged in
        VIEWING_OTHER, // Viewing another user's predictions
        USER_NOT_FOUND // Target user doesn't exist
    }

    public UserContext {
        // userId can be null for GUEST and USER_NOT_FOUND
        Objects.requireNonNull(userType, "userType is required");
    }

    /**
     * Check if this is a guest user.
     */
    public boolean isGuest() {
        return userType == UserType.GUEST;
    }

    /**
     * Check if this is an authenticated user viewing their own predictions.
     */
    public boolean isAuthenticated() {
        return userType == UserType.AUTHENTICATED;
    }

    /**
     * Check if viewing another user's predictions.
     */
    public boolean isViewingOther() {
        return userType == UserType.VIEWING_OTHER;
    }

    /**
     * Check if the target user was not found.
     */
    public boolean isUserNotFound() {
        return userType == UserType.USER_NOT_FOUND;
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
    public static UserContext authenticated(UUID userId, boolean hasContestEntry) {
        Objects.requireNonNull(userId, "userId is required for authenticated user");
        return new UserContext(userId, UserType.AUTHENTICATED, hasContestEntry);
    }

    /**
     * Create context for viewing another user's predictions.
     */
    public static UserContext viewingOther(UUID targetUserId, boolean hasContestEntry) {
        Objects.requireNonNull(targetUserId, "targetUserId is required when viewing other user");
        return new UserContext(targetUserId, UserType.VIEWING_OTHER, hasContestEntry);
    }

    /**
     * Create context when the target user was not found.
     */
    public static UserContext userNotFound() {
        return new UserContext(null, UserType.USER_NOT_FOUND, false);
    }
}
