package com.ligitabl.api.auth.impersonation;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Explicit guard for sensitive mutating endpoints (matches {@code CurrentUserId.require()}'s
 * call-site style — no AOP by design). Call {@link #assertNotBlocked()} first thing in the
 * handler.
 */
@Component
@RequiredArgsConstructor
public class ImpersonationGuard {

    private final CurrentUserFacade currentUserFacade;

    public void assertNotBlocked() {
        if (currentUserFacade.isImpersonating()) {
            throw new ImpersonationRestrictedException();
        }
    }
}
