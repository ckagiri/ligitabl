package com.ligitabl.api.auth.impersonation;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Defensively clears any {@link ImpersonationSession} on logout. The logout DSL already
 * invalidates the HTTP session; this guards against that config changing.
 */
@Component
public class ClearImpersonationLogoutHandler implements LogoutHandler {

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(ImpersonationSession.SESSION_ATTRIBUTE);
        }
    }
}
