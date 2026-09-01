package com.ligitabl.api.web.contest.contestdetail;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ligitabl.api.auth.impersonation.ImpersonationContext;
import com.ligitabl.api.auth.impersonation.UserSummary;
import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.rest.contest.getprivatecontest.GetPrivateContestError;
import com.ligitabl.api.rest.contest.getprivatecontest.GetPrivateContestQuery;
import com.ligitabl.api.rest.contest.getprivatecontest.GetPrivateContestUseCase;
import com.ligitabl.api.rest.contest.renewcontest.GetContestRenewalOptionsUseCase;
import com.ligitabl.api.rest.contest.shared.ContestSeasonSupport;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.web.contest.shared.ContestSupport;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.repo.UserRepo;

/**
 * Regression guard: contest detail already resolves through {@code WebSecurity.resolveUser}, so
 * its leaderboard follows the impersonated user. This pins that behaviour.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ContestDetailController — current user follows impersonation")
class ContestDetailControllerTest {

    @Mock
    private GetPrivateContestUseCase getPrivateContestUseCase;

    @Mock
    private GetContestRenewalOptionsUseCase getContestRenewalOptionsUseCase;

    @Mock
    private ContestSeasonSupport contestSeasonSupport;

    @Mock
    private ContestSupport contestSupport;

    @Mock
    private UserRepo userRepo;

    private ContestDetailController controller;

    private final Model model = new ExtendedModelMap();
    private final Principal principal = () -> "admin@example.com";
    private final UUID contestId = UUID.randomUUID();

    private final UserSummary admin = new UserSummary(
            UUID.randomUUID(), "admin-public-id", "admin@example.com", "Admin User", Set.of(Role.ADMIN));
    private final UserSummary target = new UserSummary(
            UUID.randomUUID(), "target-public-id", "player@example.com", "Target Player", Set.of(Role.PLAYER));

    @BeforeEach
    void setUp() {
        controller = new ContestDetailController(
                getPrivateContestUseCase,
                getContestRenewalOptionsUseCase,
                contestSeasonSupport,
                contestSupport,
                userRepo,
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("while impersonating, the query carries the impersonated user's id")
    void usesImpersonatedUserId() {
        authenticateAs(admin);
        bindRequest(ImpersonationContext.impersonating(admin, target));
        stubUseCase();

        controller.contestDetail(contestId, null, 0, null, null, model, principal);

        assertEquals(target.id(), capturedQuery().userId());
    }

    @Test
    @DisplayName("without impersonation, the query carries the logged-in user's own id")
    void usesOwnUserIdWhenNotImpersonating() {
        authenticateAs(admin);
        bindRequest(ImpersonationContext.notImpersonating(admin));
        stubUseCase();

        controller.contestDetail(contestId, null, 0, null, null, model, principal);

        assertEquals(admin.id(), capturedQuery().userId());
    }

    @Test
    @DisplayName("an anonymous request is sent to login")
    void anonymousRedirectsToLogin() {
        String view = controller.contestDetail(contestId, null, 0, null, null, model, null);

        assertEquals("redirect:/auth/login", view);
        verifyNoInteractions(getPrivateContestUseCase);
    }

    private GetPrivateContestQuery capturedQuery() {
        ArgumentCaptor<GetPrivateContestQuery> captor = ArgumentCaptor.forClass(GetPrivateContestQuery.class);
        verify(getPrivateContestUseCase).execute(captor.capture());
        return captor.getValue();
    }

    /** The error path is enough: only the query's user id is under test here. */
    private void stubUseCase() {
        when(getPrivateContestUseCase.execute(any()))
                .thenReturn(Either.left(new GetPrivateContestError.ContestNotFound(contestId)));
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
