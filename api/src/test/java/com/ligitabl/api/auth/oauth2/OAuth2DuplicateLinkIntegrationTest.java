package com.ligitabl.api.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

/**
 * Integration tests for the duplicate Google account linking scenario in
 * {@link OAuth2AuthenticationSuccessHandler}.
 *
 * <p>Verifies that when a user tries to link a Google account already belonging to another user:
 * <ul>
 *   <li>the original user's session auth is restored (not left as OAuth2/null),</li>
 *   <li>an error feedback message is stored in session,</li>
 *   <li>the response redirects to /settings/connected-accounts.</li>
 * </ul>
 */
class OAuth2DuplicateLinkIntegrationTest {

    private final UserRepo userRepo = Mockito.mock(UserRepo.class);
    private final CustomOAuth2UserService customOAuth2UserService = Mockito.mock(CustomOAuth2UserService.class);
    private final OAuth2AuthenticationSuccessHandler handler =
            new OAuth2AuthenticationSuccessHandler(userRepo, customOAuth2UserService);

    private static final String GOOGLE_SUBJECT = "google-subject-xyz";
    private static final UUID CURRENT_USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();

    @Test
    @DisplayName("duplicate Google link: redirects with error message and restores original session user")
    void duplicateLink_restoresSessionAndSetsErrorMessage() throws Exception {
        User currentUser = makeUser(CURRENT_USER_ID, "alice@example.com", "Alice", null);
        User otherUser = makeUser(OTHER_USER_ID, "bob@example.com", "Bob", GOOGLE_SUBJECT);

        when(userRepo.findById(CURRENT_USER_ID)).thenReturn(Optional.of(currentUser));
        when(userRepo.findByGoogleId(GOOGLE_SUBJECT)).thenReturn(Optional.of(otherUser));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(OAuth2AuthenticationSuccessHandler.LINKING_MODE_SESSION_KEY, true);
        session.setAttribute(OAuth2AuthenticationSuccessHandler.LINKING_USER_ID_SESSION_KEY, CURRENT_USER_ID);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        OAuth2AuthenticationToken oauthToken = makeOAuth2Token(GOOGLE_SUBJECT, "eve@gmail.com");

        handler.onAuthenticationSuccess(request, response, oauthToken);

        // Redirected to connected accounts
        assertThat(response.getRedirectedUrl()).isEqualTo("/settings/connected-accounts");

        // Error feedback stored in session
        assertThat(session.getAttribute(OAuth2AuthenticationSuccessHandler.LINKING_FEEDBACK_MESSAGE_SESSION_KEY))
                .isEqualTo("That Google account is already linked to another LigiPredictor account.");
        assertThat(session.getAttribute(OAuth2AuthenticationSuccessHandler.LINKING_FEEDBACK_TYPE_SESSION_KEY))
                .isEqualTo("error");

        // Linking state cleaned up
        assertThat(session.getAttribute(OAuth2AuthenticationSuccessHandler.LINKING_MODE_SESSION_KEY))
                .isNull();
        assertThat(session.getAttribute(OAuth2AuthenticationSuccessHandler.LINKING_USER_ID_SESSION_KEY))
                .isNull();

        // Original user's session auth is restored (not left as OAuth2/anonymous)
        var secCtx = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(secCtx).isNotNull().isInstanceOf(org.springframework.security.core.context.SecurityContext.class);

        var authentication = ((org.springframework.security.core.context.SecurityContext) secCtx).getAuthentication();
        assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(authentication.getPrincipal()).isInstanceOf(com.ligitabl.api.auth.security.WebUserDetails.class);

        var restoredUser = (com.ligitabl.api.auth.security.WebUserDetails) authentication.getPrincipal();
        assertThat(restoredUser.getUserId()).isEqualTo(CURRENT_USER_ID);
        assertThat(restoredUser.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("duplicate Google link: does not call userRepo.update (no data mutation on conflict)")
    void duplicateLink_doesNotMutateUser() throws Exception {
        User currentUser = makeUser(CURRENT_USER_ID, "alice@example.com", "Alice", null);
        User otherUser = makeUser(OTHER_USER_ID, "bob@example.com", "Bob", GOOGLE_SUBJECT);

        when(userRepo.findById(CURRENT_USER_ID)).thenReturn(Optional.of(currentUser));
        when(userRepo.findByGoogleId(GOOGLE_SUBJECT)).thenReturn(Optional.of(otherUser));

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(OAuth2AuthenticationSuccessHandler.LINKING_MODE_SESSION_KEY, true);
        session.setAttribute(OAuth2AuthenticationSuccessHandler.LINKING_USER_ID_SESSION_KEY, CURRENT_USER_ID);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, makeOAuth2Token(GOOGLE_SUBJECT, "eve@gmail.com"));

        Mockito.verify(userRepo, Mockito.never()).update(Mockito.any());
    }

    // ---- helpers ----

    private User makeUser(UUID id, String email, String displayName, String googleId) {
        return new User(
                id,
                PublicId.create("ABCDE23456"),
                Email.create(email),
                displayName,
                null,
                Set.of(Role.PLAYER),
                true,
                googleId);
    }

    private OAuth2AuthenticationToken makeOAuth2Token(String googleSubject, String email) {
        Map<String, Object> attributes = Map.of(
                "sub", googleSubject,
                "email", email,
                "name", "Eve");
        DefaultOAuth2User oAuth2User =
                new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), attributes, "sub");
        return new OAuth2AuthenticationToken(oAuth2User, List.of(new SimpleGrantedAuthority("ROLE_USER")), "google");
    }
}
