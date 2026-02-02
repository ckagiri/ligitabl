package com.ligitabl.api.web.shared.security;

import com.ligitabl.api.auth.security.WebUserDetails;
import java.security.Principal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class WebSecurity {
    private WebSecurity() {
    }

    public static WebUserDetails resolveUser(Principal principal) {
        if (principal == null) {
            return null;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof WebUserDetails details) {
            return details;
        }

        return null;
    }
}
