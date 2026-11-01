package com.ligitabl.api.auth.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import com.ligitabl.api.usecase.auth.recordlogin.RecordLoginUseCase;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The only hook for tracking last-login on remember-me auto-logins: {@code RememberMeAuthenticationFilter}
 * bypasses the form-login controller and {@code OAuth2AuthenticationSuccessHandler}, and publishes
 * only this event.
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
