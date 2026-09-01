package com.ligitabl.api.web.leaderboard;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.security.Principal;
import java.util.List;
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

import com.ligitabl.api.auth.impersonation.ImpersonationContext;
import com.ligitabl.api.auth.impersonation.UserSummary;
import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.rest.leaderboard.getleaderboard.GetLeaderboardQuery;
import com.ligitabl.api.rest.leaderboard.getleaderboard.GetLeaderboardResult;
import com.ligitabl.api.rest.leaderboard.getleaderboard.GetLeaderboardUseCase;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.domain.LeaderboardEntry;
import com.ligitabl.model.domain.PhaseType;
import com.ligitabl.model.domain.RoundSpan;

import jakarta.servlet.http.HttpServletResponse;

/**
 * The leaderboard's "your row" must follow the effective user: while an admin impersonates a
 * player, the highlighted row and the pinned footer describe the player, not the admin.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GetLeaderboardController — current user follows impersonation")
class GetLeaderboardControllerTest {

    @Mock
    private GetLeaderboardUseCase getLeaderboardUseCase;

    @Mock
    private HttpServletResponse response;

    private GetLeaderboardController controller;

    private final Model model = new ExtendedModelMap();
    private final Principal principal = () -> "admin@example.com";

    private final UserSummary admin = new UserSummary(
            UUID.randomUUID(), "admin-public-id", "admin@example.com", "Admin User", Set.of(Role.ADMIN));
    private final UserSummary target = new UserSummary(
            UUID.randomUUID(), "target-public-id", "player@example.com", "Target Player", Set.of(Role.PLAYER));

    @BeforeEach
    void setUp() {
        controller = new GetLeaderboardController(getLeaderboardUseCase);
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
        impersonate(admin, target);
        stubUseCase();

        controller.getLeaderboard(null, 1, principal, model, response, null);

        assertEquals(target.id(), capturedQuery().userId());
    }

    @Test
    @DisplayName("while impersonating, the pinned footer name is the impersonated user's")
    void usesImpersonatedDisplayName() {
        authenticateAs(admin);
        impersonate(admin, target);
        stubUseCase();

        controller.getLeaderboard(null, 1, principal, model, response, null);

        assertEquals("Target Player", model.getAttribute("currentUserName"));
    }

    @Test
    @DisplayName("without impersonation, the query carries the logged-in user's own id")
    void usesOwnUserIdWhenNotImpersonating() {
        authenticateAs(admin);
        bindRequest(ImpersonationContext.notImpersonating(admin));
        stubUseCase();

        controller.getLeaderboard(null, 1, principal, model, response, null);

        assertEquals(admin.id(), capturedQuery().userId());
        assertEquals("Admin User", model.getAttribute("currentUserName"));
    }

    @Test
    @DisplayName("an anonymous request has no current user")
    void anonymousHasNoCurrentUser() {
        stubUseCase();

        controller.getLeaderboard(null, 1, null, model, response, null);

        assertNull(capturedQuery().userId());
        assertNull(model.getAttribute("currentUserName"));
    }

    private GetLeaderboardQuery capturedQuery() {
        ArgumentCaptor<GetLeaderboardQuery> captor = ArgumentCaptor.forClass(GetLeaderboardQuery.class);
        verify(getLeaderboardUseCase).execute(captor.capture());
        return captor.getValue();
    }

    private void stubUseCase() {
        when(getLeaderboardUseCase.execute(any())).thenReturn(Either.right(emptyResult()));
    }

    private static GetLeaderboardResult emptyResult() {
        RoundSpan phase = RoundSpan.builder()
                .code("overall")
                .name("Overall")
                .from(1)
                .to(38)
                .type(PhaseType.FULL_SEASON)
                .build();
        return new GetLeaderboardResult(
                UUID.randomUUID(),
                phase,
                null,
                null,
                1,
                List.<LeaderboardEntry>of(),
                List.of(phase),
                null,
                false,
                0,
                0,
                false,
                false,
                0,
                10);
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

    private void impersonate(UserSummary original, UserSummary effective) {
        bindRequest(ImpersonationContext.impersonating(original, effective));
    }

    /** Mirrors {@code ImpersonationSessionFilter}: the context lives on the request. */
    private void bindRequest(ImpersonationContext context) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ImpersonationContext.REQUEST_ATTRIBUTE, context);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
