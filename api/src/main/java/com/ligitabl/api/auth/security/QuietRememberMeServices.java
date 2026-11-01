package com.ligitabl.api.auth.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Wraps a persistent-token {@link RememberMeServices} so a stale or reused remember-me cookie never
 * surfaces as an uncaught {@code CookieTheftException}, whatever path calls {@code autoLogin}.
 * The mismatch is usually a DB reset or two requests racing one cookie, not an attack; the
 * request is treated as logged out, with one log line and the cookie cleared via {@code loginFail}.
 */
@RequiredArgsConstructor
@Slf4j
public class QuietRememberMeServices implements RememberMeServices {

    private final RememberMeServices delegate;

    @Override
    public Authentication autoLogin(HttpServletRequest request, HttpServletResponse response) {
        try {
            return delegate.autoLogin(request, response);
        } catch (RememberMeAuthenticationException e) {
            log.warn(
                    "[REMEMBER_ME_STALE_COOKIE] {} -- clearing cookie, treating as logged out."
                            + " Usually benign (DB reset, or two requests racing the same cookie), not necessarily theft.",
                    e.getMessage());
            delegate.loginFail(request, response);
            return null;
        }
    }

    @Override
    public void loginFail(HttpServletRequest request, HttpServletResponse response) {
        delegate.loginFail(request, response);
    }

    @Override
    public void loginSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication successfulAuthentication) {
        delegate.loginSuccess(request, response, successfulAuthentication);
    }
}
