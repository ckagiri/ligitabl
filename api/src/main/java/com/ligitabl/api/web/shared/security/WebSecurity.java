package com.ligitabl.api.web.shared.security;

import java.security.Principal;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.ligitabl.api.auth.impersonation.ImpersonationContext;
import com.ligitabl.api.auth.impersonation.UserSummary;
import com.ligitabl.api.auth.security.WebUserDetails;

public final class WebSecurity {
    private WebSecurity() {}

    /**
     * Resolve the user this request should act as. While an admin is impersonating, this
     * returns the effective (impersonated) user — data flows follow the effective identity;
     * permission checks stay on the real principal via Spring Security's authorities.
     */
    public static WebUserDetails resolveUser(Principal principal) {
        if (principal == null) {
            return null;
        }

        ImpersonationContext impersonation = currentImpersonationContext();
        if (impersonation != null && impersonation.impersonating()) {
            return toEffectiveDetails(impersonation.effective());
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof WebUserDetails details) {
            return details;
        }

        return null;
    }

    private static ImpersonationContext currentImpersonationContext() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        Object value = attributes.getRequest().getAttribute(ImpersonationContext.REQUEST_ATTRIBUTE);
        return value instanceof ImpersonationContext context ? context : null;
    }

    private static WebUserDetails toEffectiveDetails(UserSummary effective) {
        return new WebUserDetails(
                effective.id(),
                effective.publicId(),
                effective.email(),
                effective.displayName(),
                null,
                effective.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                        .collect(Collectors.toSet()));
    }
}
