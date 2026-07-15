package com.ligitabl.api.auth.impersonation;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.repo.UserRepo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * Resolves the request's {@link ImpersonationContext} from the security principal plus any
 * {@link ImpersonationSession} in the HTTP session.
 *
 * <p>Order note: unlike {@code RequestLoggingFilter}/{@code RateLimitFilter} (which run before
 * Spring Security), this must run AFTER the security filter chain (registered at
 * {@code SecurityProperties.DEFAULT_FILTER_ORDER} = -100) so the authenticated principal is
 * available — hence {@code @Order(0)}.
 *
 * <p>Registered via {@code ImpersonationConfig} (not {@code @Component}) so MVC slice tests,
 * which instantiate component-scanned filters but have no {@code UserRepo} bean, never load it.
 */
@Order(0)
@RequiredArgsConstructor
public class ImpersonationSessionFilter extends OncePerRequestFilter {

    private final UserRepo userRepo;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof WebUserDetails details) {
            UserSummary original = toSummary(details, authentication);
            request.setAttribute(
                    ImpersonationContext.REQUEST_ATTRIBUTE, resolveContext(request, original));
        }

        filterChain.doFilter(request, response);
    }

    private ImpersonationContext resolveContext(HttpServletRequest request, UserSummary original) {
        HttpSession session = request.getSession(false);
        Object attribute =
                session == null ? null : session.getAttribute(ImpersonationSession.SESSION_ATTRIBUTE);

        if (!(attribute instanceof ImpersonationSession impersonation)) {
            return ImpersonationContext.notImpersonating(original);
        }

        // Defensive: a stale session written by a different principal is ignored
        if (!original.id().equals(impersonation.originalUserId())) {
            session.removeAttribute(ImpersonationSession.SESSION_ATTRIBUTE);
            return ImpersonationContext.notImpersonating(original);
        }

        return userRepo.findById(impersonation.targetUserId())
                .map(target -> ImpersonationContext.impersonating(original, UserSummary.from(target)))
                .orElseGet(() -> {
                    // Target deleted mid-impersonation — drop the session and fall back
                    session.removeAttribute(ImpersonationSession.SESSION_ATTRIBUTE);
                    return ImpersonationContext.notImpersonating(original);
                });
    }

    private static UserSummary toSummary(WebUserDetails details, Authentication authentication) {
        Set<Role> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(name -> name.startsWith("ROLE_"))
                .map(name -> parseRole(name.substring("ROLE_".length())))
                .filter(role -> role != null)
                .collect(Collectors.toSet());
        return new UserSummary(
                details.getUserId(), details.getPublicId(), details.getEmail(), details.getDisplayName(), roles);
    }

    private static Role parseRole(String value) {
        try {
            return Role.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
