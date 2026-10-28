package com.ligitabl.api.auth.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import com.ligitabl.api.usecase.auth.recordlogin.RecordLoginUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Remember-me auto-login authenticates via {@code RememberMeAuthenticationFilter}, which bypasses
 * both the form-login controller and {@code OAuth2AuthenticationSuccessHandler} entirely — it only
 * publishes this event, never an {@code AuthenticationSuccessHandler} callback. This is the only
 * hook point for tracking last-login on cookie-based auto-logins.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RememberMeLoginListener {

    private final RecordLoginUseCase recordLoginUseCase;

    @EventListener
    public void onAuthenticationSuccess(InteractiveAuthenticationSuccessEvent event) {
        if (!(event.getAuthentication() instanceof RememberMeAuthenticationToken)) {
            return;
        }

        if (event.getAuthentication().getPrincipal() instanceof WebUserDetails webUserDetails) {
            recordLoginUseCase.execute(webUserDetails.getUserId());
            log.info("[REMEMBER_ME_LOGIN] userId={}", webUserDetails.getUserId());
        }
    }
}
