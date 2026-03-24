package com.ligitabl.api.auth.oauth2;

import java.io.IOException;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2AuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    public static final String LINKING_MODE_SESSION_KEY = "OAUTH2_LINKING_MODE";
    public static final String LINKING_USER_ID_SESSION_KEY = "OAUTH2_LINKING_USER_ID";

    private final UserRepo userRepo;
    private final CustomOAuth2UserService customOAuth2UserService;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        if (!(authentication.getPrincipal() instanceof OAuth2User)) {
            throw new IllegalStateException("Unexpected OAuth2 principal type: "
                    + authentication.getPrincipal().getClass());
        }

        HttpSession session = request.getSession(true);
        UUID linkingUserId = linkingUserId(session);
        boolean linkingMode = Boolean.TRUE.equals(session.getAttribute(LINKING_MODE_SESSION_KEY));

        if (linkingMode && linkingUserId != null) {
            handleAccountLinking(request, response, authentication, linkingUserId, session);
        } else {
            handleNormalLogin(request, response, authentication);
        }
    }

    private void handleAccountLinking(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication,
            UUID userId,
            HttpSession session)
            throws IOException {
        OAuth2UserInfo userInfo = oauth2UserInfo(authentication);

        log.info("[LINKING_GOOGLE_ACCOUNT] userId={} googleId={} email={}", userId, userInfo.id(), userInfo.email());

        User existingUser =
                userRepo.findById(userId).orElseThrow(() -> new IllegalStateException("Could not find user to link"));

        if (userInfo.id() == null || userInfo.id().isBlank()) {
            throw new IllegalStateException("Google id missing during account linking");
        }

        userRepo.findByGoogleId(userInfo.id()).ifPresent(otherUser -> {
            if (!otherUser.getId().equals(userId)) {
                log.warn(
                        "[GOOGLE_ALREADY_LINKED] googleId={} already linked to userId={}",
                        userInfo.id(),
                        otherUser.getId());
                throw new IllegalStateException("This Google account is already linked to another user");
            }
        });

        User updatedUser = new User(
                existingUser.getId(),
                existingUser.getPublicId(),
                existingUser.getEmail(),
                existingUser.getDisplayName(),
                existingUser.getPassword(),
                existingUser.getRoles(),
                true,
                userInfo.id());

        userRepo.update(updatedUser);
        log.info("[GOOGLE_LINKED_SUCCESS] userId={} googleId={}", updatedUser.getId(), userInfo.id());

        clearLinkingState(session);
        session.setAttribute("accountLinkingSuccess", true);

        establishSessionAuthentication(updatedUser, session);
        getRedirectStrategy().sendRedirect(request, response, "/settings/connected-accounts?linked=1");
    }

    private void handleNormalLogin(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        User user = authentication.getPrincipal() instanceof LigitablOAuth2User oauth2User
                ? oauth2User.getUser()
                : customOAuth2UserService.findOrCreateUser(oauth2Attributes(authentication));

        establishSessionAuthentication(user, request.getSession(true));
        log.info(
                "[OAUTH2_LOGIN_SUCCESS] userId={} email={}",
                user.getId(),
                user.getEmail().value());

        getRedirectStrategy().sendRedirect(request, response, "/my-table");
    }

    private void establishSessionAuthentication(User user, HttpSession session) {
        var authorities = user.getRoles().stream()
                .map(role ->
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();

        WebUserDetails webUserDetails = new WebUserDetails(
                user.getId(),
                user.getPublicId().value(),
                user.getEmail().value(),
                user.getDisplayName(),
                "",
                authorities);

        UsernamePasswordAuthenticationToken webAuthentication =
                new UsernamePasswordAuthenticationToken(webUserDetails, null, authorities);

        SecurityContextHolder.getContext().setAuthentication(webAuthentication);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());
    }

    private OAuth2UserInfo oauth2UserInfo(Authentication authentication) {
        return OAuth2UserInfo.fromGoogle(oauth2Attributes(authentication));
    }

    private java.util.Map<String, Object> oauth2Attributes(Authentication authentication) {
        if (authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            return oauth2User.getAttributes();
        }
        throw new IllegalStateException("Unexpected OAuth2 principal type");
    }

    private UUID linkingUserId(HttpSession session) {
        Object value = session.getAttribute(LINKING_USER_ID_SESSION_KEY);
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return UUID.fromString(stringValue);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private void clearLinkingState(HttpSession session) {
        session.removeAttribute(LINKING_MODE_SESSION_KEY);
        session.removeAttribute(LINKING_USER_ID_SESSION_KEY);
    }
}
