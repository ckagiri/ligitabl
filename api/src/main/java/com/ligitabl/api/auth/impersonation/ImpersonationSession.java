package com.ligitabl.api.auth.impersonation;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Stored in {@link jakarta.servlet.http.HttpSession} while an admin is impersonating.
 */
public record ImpersonationSession(UUID targetUserId, String targetEmail, Instant startedAt, UUID originalUserId)
        implements Serializable {

    public static final String SESSION_ATTRIBUTE = "LIGITABL_IMPERSONATION_SESSION";
}
