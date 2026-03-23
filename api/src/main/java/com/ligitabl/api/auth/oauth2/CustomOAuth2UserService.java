package com.ligitabl.api.auth.oauth2;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.domain.service.PublicIdGenerator;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepo userRepo;
    private final PublicIdGenerator publicIdGenerator;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);
        return processOAuth2User(userRequest, oauth2User);
    }

    private OAuth2User processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oauth2User) {
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oauth2User.getAttributes();

        OAuth2UserInfo userInfo = switch (registrationId) {
            case "google" -> OAuth2UserInfo.fromGoogle(attributes);
            default -> throw new IllegalArgumentException("Unsupported OAuth2 provider: " + registrationId);
        };

        if (userInfo.id() == null || userInfo.id().isBlank()) {
            throw oauthError("Subject is missing from OAuth2 payload");
        }
        if (userInfo.email() == null || userInfo.email().isBlank()) {
            throw oauthError("Email is missing from OAuth2 payload");
        }

        User user = userRepo.findByGoogleId(userInfo.id())
                .orElseGet(() -> findByEmailOrCreate(userInfo));

        return new LigitablOAuth2User(user, oauth2User.getAttributes());
    }

    private User findByEmailOrCreate(OAuth2UserInfo userInfo) {
        Email email = Email.create(userInfo.email().trim().toLowerCase(Locale.ROOT));

        return userRepo.findByEmail(email)
                .map(existing -> linkExistingUser(existing, userInfo))
                .orElseGet(() -> createOAuth2User(email, userInfo));
    }

    private User linkExistingUser(User existing, OAuth2UserInfo userInfo) {
        String googleId = userInfo.id();

        if (existing.getGoogleId() != null && !existing.getGoogleId().equals(googleId)) {
            throw oauthError("This email is already linked to another Google account");
        }

        if (existing.getGoogleId() == null) {
            User linkedUser = new User(
                    existing.getId(),
                    existing.getPublicId(),
                    existing.getEmail(),
                    existing.getDisplayName(),
                    existing.getPassword(),
                    existing.getRoles(),
                    true,
                    googleId);
            userRepo.update(linkedUser);
            log.info("[OAUTH_GOOGLE_LINKED] userId={} email={}", existing.getId(), existing.getEmail().value());
            return linkedUser;
        }

        return existing;
    }

    private User createOAuth2User(Email email, OAuth2UserInfo userInfo) {
        UUID userId = UUID.randomUUID();
        String displayName = (userInfo.name() == null || userInfo.name().isBlank())
            ? extractNameFromEmail(email.value())
                : userInfo.name();

        User user = User.builder()
                .id(userId)
                .publicId(publicIdGenerator.generate(userId))
                .email(email)
                .displayName(displayName)
                .password(null)
                .roles(Set.of(Role.PLAYER))
                .emailVerified(true)
                .googleId(userInfo.id())
                .build();

        userRepo.create(user);
        log.info("[OAUTH_GOOGLE_CREATED] userId={} email={}", user.getId(), user.getEmail().value());
        return user;
    }

    private OAuth2AuthenticationException oauthError(String message) {
        return new OAuth2AuthenticationException(new OAuth2Error("oauth2_user_processing_error"), message);
    }

    private String extractNameFromEmail(String email) {
        if (email == null || email.isBlank()) {
            return "Player";
        }

        String localPart = email.split("@")[0];
        String extracted = capitalizeWords(localPart.replace(".", " ").replace("_", " "));
        return extracted.isBlank() ? "Player" : extracted;
    }

    private String capitalizeWords(String input) {
        String[] words = input.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return result.toString().trim();
    }
}
