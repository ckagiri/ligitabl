package com.ligitabl.api.web.predictions.latestresult;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.ligitabl.api.auth.impersonation.ImpersonationContext;
import com.ligitabl.api.auth.impersonation.UserSummary;
import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.auth.Role;

/**
 * The results banner on /my-table must describe the effective user — impersonation lands the
 * admin on that page, so the banner has to be the impersonated player's, not the admin's.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LatestResultBannerController — current user follows impersonation")
class LatestResultBannerControllerTest {

    @Mock
    private GetLatestResultUseCase getLatestResultUseCase;

    @Mock
    private DismissResultBannerUseCase dismissResultBannerUseCase;

    private LatestResultBannerController controller;

    private final Model model = new ExtendedModelMap();
    private final Principal principal = () -> "admin@example.com";

    private final UserSummary admin = new UserSummary(
            UUID.randomUUID(), "admin-public-id", "admin@example.com", "Admin User", Set.of(Role.ADMIN));
    private final UserSummary target = new UserSummary(
            UUID.randomUUID(), "target-public-id", "player@example.com", "Target Player", Set.of(Role.PLAYER));

    @BeforeEach
    void setUp() {
        controller = new LatestResultBannerController(getLatestResultUseCase, dismissResultBannerUseCase);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("while impersonating, the banner is fetched for the impersonated user")
    void fetchesBannerForImpersonatedUser() {
        authenticateAs(admin);
        bindRequest(ImpersonationContext.impersonating(admin, target));
        when(getLatestResultUseCase.execute(target.id())).thenReturn(Either.right(Optional.empty()));

        controller.getLatestResultBanner(principal, model);

        verify(getLatestResultUseCase).execute(target.id());
        verify(getLatestResultUseCase, never()).execute(admin.id());
    }

    @Test
    @DisplayName("while impersonating, dismissal is recorded against the impersonated user")
    void dismissesForImpersonatedUser() {
        authenticateAs(admin);
        bindRequest(ImpersonationContext.impersonating(admin, target));
        when(dismissResultBannerUseCase.execute(target.id(), 7)).thenReturn(Either.right(null));

        controller.dismissBanner(7, principal);

        verify(dismissResultBannerUseCase).execute(target.id(), 7);
    }

    @Test
    @DisplayName("without impersonation, the banner is fetched for the logged-in user")
    void fetchesBannerForOwnUserWhenNotImpersonating() {
        authenticateAs(admin);
        bindRequest(ImpersonationContext.notImpersonating(admin));
        when(getLatestResultUseCase.execute(admin.id())).thenReturn(Either.right(Optional.empty()));

        controller.getLatestResultBanner(principal, model);

        verify(getLatestResultUseCase).execute(admin.id());
    }

    @Test
    @DisplayName("an anonymous request is rejected on dismiss and renders an empty banner")
    void anonymousIsRejected() {
        assertEquals(401, controller.dismissBanner(7, null).getStatusCode().value());
        verifyNoInteractions(dismissResultBannerUseCase);

        controller.getLatestResultBanner(null, model);
        verifyNoInteractions(getLatestResultUseCase);
    }

    private void authenticateAs(UserSummary user) {
        WebUserDetails details = new WebUserDetails(
                user.id(),
                user.publicId(),
                user.email(),
                user.displayName(),
                null,
                user.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                        .toList());
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities()));
    }

    /** Mirrors {@code ImpersonationSessionFilter}: the context lives on the request. */
    private void bindRequest(ImpersonationContext context) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ImpersonationContext.REQUEST_ATTRIBUTE, context);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
