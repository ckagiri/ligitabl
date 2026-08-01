package com.ligitabl.api.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.CookieTheftException;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;

/**
 * Unit tests for {@link RaceTolerantPersistentTokenRememberMeServices}.
 *
 * <p>The behaviour that matters most: a token mismatch caused by two of the same
 * browser's requests racing must NOT wipe the user's remember-me tokens, while a
 * genuinely stale mismatch still must.
 */
class RaceTolerantPersistentTokenRememberMeServicesTest {

    private static final String SERIES = "series-abc";
    private static final String STORED_TOKEN = "stored-token";
    private static final String USERNAME = "ada@example.com";
    private static final int VALIDITY_SECONDS = 1_209_600; // 14 days, as configured in application.yml

    private PersistentTokenRepository tokenRepository;
    private RaceTolerantPersistentTokenRememberMeServices services;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        tokenRepository = mock(PersistentTokenRepository.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(userDetailsService.loadUserByUsername(USERNAME))
                .thenReturn(User.withUsername(USERNAME)
                        .password("n/a")
                        .roles("PLAYER")
                        .build());

        services = new RaceTolerantPersistentTokenRememberMeServices("test-key", userDetailsService, tokenRepository);
        services.setTokenValiditySeconds(VALIDITY_SECONDS);

        request = new MockHttpServletRequest("GET", "/my-table");
        response = new MockHttpServletResponse();
    }

    /** Stores a token whose {@code last_used} is {@code ageMs} milliseconds in the past. */
    private void givenStoredToken(long ageMs) {
        when(tokenRepository.getTokenForSeries(SERIES))
                .thenReturn(new PersistentRememberMeToken(
                        USERNAME, SERIES, STORED_TOKEN, new Date(System.currentTimeMillis() - ageMs)));
    }

    /**
     * Decodes the series/token pair Spring writes into the remember-me cookie. Spring
     * URL-encodes each component (generated tokens are base64 and contain '/' and '='), joins
     * them with ':', and base64-encodes the result — so unwind it in that order.
     */
    private String[] cookieTokens() {
        var cookie = response.getCookie("remember-me");
        if (cookie == null) {
            throw new AssertionError("remember-me cookie should have been set");
        }
        var decoded = new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
        return Arrays.stream(decoded.split(":"))
                // '+' is legal base64 but URLDecoder would read it as a space, so protect it.
                .map(part -> URLDecoder.decode(part.replace("+", "%2B"), StandardCharsets.UTF_8))
                .toArray(String[]::new);
    }

    @Test
    @DisplayName("Matching token rotates the value, keeps the series, and re-cookies")
    void matchingToken_rotates() {
        givenStoredToken(60_000L);

        var userDetails = services.processAutoLoginCookie(new String[] {SERIES, STORED_TOKEN}, request, response);

        assertThat(userDetails.getUsername()).isEqualTo(USERNAME);

        var newTokenValue = ArgumentCaptor.forClass(String.class);
        verify(tokenRepository).updateToken(eq(SERIES), newTokenValue.capture(), any(Date.class));
        assertThat(newTokenValue.getValue()).isNotEqualTo(STORED_TOKEN);

        verify(tokenRepository, never()).removeUserTokens(anyString());
        assertThat(cookieTokens()).containsExactly(SERIES, newTokenValue.getValue());
    }

    @Test
    @DisplayName("Mismatch inside the grace window logs in without rotating or deleting anything")
    void mismatchWithinGraceWindow_isTreatedAsConcurrentRequest() {
        // A sibling request rotated the token a moment ago; this one still holds the old value.
        givenStoredToken(RaceTolerantPersistentTokenRememberMeServices.GRACE_WINDOW_MS / 2);

        var userDetails = services.processAutoLoginCookie(new String[] {SERIES, "previous-token"}, request, response);

        assertThat(userDetails.getUsername()).isEqualTo(USERNAME);

        // This is the whole point: the losing request must not destroy the user's logins.
        verify(tokenRepository, never()).removeUserTokens(anyString());
        verify(tokenRepository, never()).updateToken(anyString(), anyString(), any(Date.class));

        // The browser is handed the token currently in the store, so it converges on it.
        assertThat(cookieTokens()).containsExactly(SERIES, STORED_TOKEN);
    }

    @Test
    @DisplayName("Mismatch outside the grace window still deletes all user tokens and reports theft")
    void mismatchOutsideGraceWindow_isTreatedAsTheft() {
        givenStoredToken(RaceTolerantPersistentTokenRememberMeServices.GRACE_WINDOW_MS + 5_000L);

        assertThatThrownBy(
                        () -> services.processAutoLoginCookie(new String[] {SERIES, "stolen-token"}, request, response))
                .isInstanceOf(CookieTheftException.class);

        verify(tokenRepository).removeUserTokens(USERNAME);
    }

    @Test
    @DisplayName("Expired token is rejected without deleting the user's other logins")
    void expiredToken_isRejected() {
        givenStoredToken((VALIDITY_SECONDS * 1000L) + 60_000L);

        assertThatThrownBy(
                        () -> services.processAutoLoginCookie(new String[] {SERIES, STORED_TOKEN}, request, response))
                .isInstanceOf(RememberMeAuthenticationException.class)
                .hasMessageContaining("expired");

        verify(tokenRepository, never()).removeUserTokens(anyString());
    }

    @Test
    @DisplayName("Unknown series is rejected")
    void unknownSeries_isRejected() {
        when(tokenRepository.getTokenForSeries("no-such-series")).thenReturn(null);

        assertThatThrownBy(
                        () -> services.processAutoLoginCookie(new String[] {"no-such-series", "t"}, request, response))
                .isInstanceOf(RememberMeAuthenticationException.class);

        verify(tokenRepository, never()).removeUserTokens(anyString());
    }

    @Test
    @DisplayName("Malformed cookie is rejected")
    void malformedCookie_isRejected() {
        assertThatThrownBy(() -> services.processAutoLoginCookie(new String[] {"only-one-part"}, request, response))
                .isInstanceOf(InvalidCookieException.class);
    }

    @Test
    @DisplayName("A data-access failure during rotation fails the login without deleting tokens")
    void updateFailure_failsLoginQuietly() {
        givenStoredToken(60_000L);
        doThrowOnUpdate();

        assertThatThrownBy(
                        () -> services.processAutoLoginCookie(new String[] {SERIES, STORED_TOKEN}, request, response))
                .isInstanceOf(RememberMeAuthenticationException.class)
                .hasMessageContaining("data access problem");

        verify(tokenRepository, never()).removeUserTokens(anyString());
    }

    private void doThrowOnUpdate() {
        org.mockito.Mockito.doThrow(new IllegalStateException("db down"))
                .when(tokenRepository)
                .updateToken(anyString(), anyString(), any(Date.class));
    }
}
