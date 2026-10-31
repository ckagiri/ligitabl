package com.ligitabl.api.auth.oauth2;

import java.util.Map;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.ligitabl.api.usecase.auth.googlesignin.GoogleIdentity;
import com.ligitabl.api.usecase.auth.googlesignin.SignInWithGoogleError;
import com.ligitabl.api.usecase.auth.googlesignin.SignInWithGoogleUseCase;
import com.ligitabl.model.domain.identity.User;

import lombok.RequiredArgsConstructor;

/** Validates Google's payload, then hands the sign-in to {@link SignInWithGoogleUseCase}. */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final SignInWithGoogleUseCase signInWithGoogleUseCase;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = super.loadUser(userRequest);
        return processOAuth2User(userRequest, oauth2User);
    }

    public User findOrCreateUser(Map<String, Object> attributes) {
        OAuth2UserInfo userInfo = OAuth2UserInfo.fromGoogle(attributes);

        if (userInfo.id() == null || userInfo.id().isBlank()) {
            throw oauthError("Subject is missing from OAuth2 payload");
        }
        if (userInfo.email() == null || userInfo.email().isBlank()) {
            throw oauthError("Email is missing from OAuth2 payload");
        }

        return signInWithGoogleUseCase
                .execute(new GoogleIdentity(userInfo.id(), userInfo.email(), userInfo.name()))
                .fold(
                        error -> {
                            throw oauthError(message(error));
                        },
                        user -> user);
    }

    private static String message(SignInWithGoogleError error) {
        return switch (error) {
            case SignInWithGoogleError.EmailLinkedToOtherGoogleAccount
            __ -> "This email is already linked to another Google account";
        };
    }

    private OAuth2User processOAuth2User(OAuth2UserRequest userRequest, OAuth2User oauth2User) {
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        Map<String, Object> attributes = oauth2User.getAttributes();

        OAuth2UserInfo userInfo =
                switch (registrationId) {
                    case "google" -> OAuth2UserInfo.fromGoogle(attributes);
                    default -> throw new IllegalArgumentException("Unsupported OAuth2 provider: " + registrationId);
                };

        if (userInfo.id() == null || userInfo.id().isBlank()) {
            throw oauthError("Subject is missing from OAuth2 payload");
        }
        if (userInfo.email() == null || userInfo.email().isBlank()) {
            throw oauthError("Email is missing from OAuth2 payload");
        }

        User user = findOrCreateUser(attributes);

        return new LigitablOAuth2User(user, oauth2User.getAttributes());
    }

    private OAuth2AuthenticationException oauthError(String message) {
        return new OAuth2AuthenticationException(new OAuth2Error("oauth2_user_processing_error"), message);
    }
}
