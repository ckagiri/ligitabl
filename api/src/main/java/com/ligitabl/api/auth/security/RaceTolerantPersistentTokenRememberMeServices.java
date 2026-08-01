package com.ligitabl.api.auth.security;

import java.util.Arrays;
import java.util.Date;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.CookieTheftException;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * A {@link PersistentTokenBasedRememberMeServices} that tolerates the token-rotation
 * race caused by concurrent requests from the same browser.
 *
 * <p><b>The problem.</b> Persistent-token remember-me rotates the token on every
 * auto-login, keeping the same series. Spring's implementation treats <em>any</em>
 * series/token mismatch as cookie theft and responds by calling
 * {@code removeUserTokens(username)} — deleting every remember-me token that user has,
 * on every device — before throwing {@link CookieTheftException}.
 *
 * <p>That is far too blunt here, because a single page load produces several
 * <em>parallel</em> requests that all carry the same cookie. Static assets
 * ({@code /css/**}, {@code /dist/**}, {@code /js/**}, {@code /images/**}) are
 * {@code permitAll()} but still run through the security filter chain — {@code permitAll}
 * skips authorization, not the filters — so each one hits
 * {@code RememberMeAuthenticationFilter} with an empty security context and calls
 * {@code autoLogin}. Whichever request wins rotates the token; the others are still
 * holding the previous value, because the winner's {@code Set-Cookie} has not reached
 * the browser yet. Under stock Spring, those losers wipe the user's tokens and force a
 * full re-login. This fires reliably on the first page load after every deploy, when no
 * session exists and every request falls back to the cookie.
 *
 * <p><b>The fix.</b> A mismatch is only treated as theft once the stored token has been
 * sitting untouched for longer than {@link #GRACE_WINDOW_MS}. Inside that window the
 * mismatch is read as "a sibling request just rotated this token", so the login is
 * accepted, the token store is left alone, and the cookie currently in the store is
 * re-issued — which also repairs the browser's cookie if the winner's {@code Set-Cookie}
 * was lost.
 *
 * <p>This does not meaningfully weaken theft detection. An attacker replaying a stolen
 * cookie outside the window still trips the original path in full; to land inside it
 * they would have to race the legitimate browser's own in-flight requests, within a
 * minute of that browser's last auto-login.
 *
 * <p>Every other branch — malformed cookie, unknown series, expiry, and the normal
 * matching-token rotation — behaves exactly as in
 * {@link PersistentTokenBasedRememberMeServices}, including ordering.
 *
 * @see QuietRememberMeServices which stops a genuine {@link CookieTheftException} from
 *     surfacing as an error page, but cannot prevent the token deletion that happens
 *     before it is thrown
 */
@Slf4j
public class RaceTolerantPersistentTokenRememberMeServices extends PersistentTokenBasedRememberMeServices {

    /**
     * How long after a token was last rotated a mismatch is still assumed to be a
     * concurrent request from the same browser rather than a stolen cookie. Needs only
     * to cover the spread of one page load's parallel requests; a minute is generous.
     */
    static final long GRACE_WINDOW_MS = 60_000L;

    /** Own reference: the superclass keeps its copy private with no accessor. */
    private final PersistentTokenRepository tokenRepository;

    public RaceTolerantPersistentTokenRememberMeServices(
            String key, UserDetailsService userDetailsService, PersistentTokenRepository tokenRepository) {
        super(key, userDetailsService, tokenRepository);
        this.tokenRepository = tokenRepository;
    }

    @Override
    protected UserDetails processAutoLoginCookie(
            String[] cookieTokens, HttpServletRequest request, HttpServletResponse response) {
        if (cookieTokens.length != 2) {
            throw new InvalidCookieException(
                    "Cookie token did not contain 2 tokens, but contained '" + Arrays.asList(cookieTokens) + "'");
        }
        String presentedSeries = cookieTokens[0];
        String presentedToken = cookieTokens[1];

        PersistentRememberMeToken token = tokenRepository.getTokenForSeries(presentedSeries);
        if (token == null) {
            throw new RememberMeAuthenticationException("No persistent token found for series id: " + presentedSeries);
        }

        if (!presentedToken.equals(token.getTokenValue())) {
            long ageMs = System.currentTimeMillis() - token.getDate().getTime();
            if (ageMs > GRACE_WINDOW_MS) {
                // Stale enough that a concurrent request can't explain it — original behaviour.
                tokenRepository.removeUserTokens(token.getUsername());
                throw new CookieTheftException(messages.getMessage(
                        "PersistentTokenBasedRememberMeServices.cookieStolen",
                        "Invalid remember-me token (Series/token) mismatch. Implies previous cookie theft attack."));
            }
            // A sibling request rotated this token moments ago. Accept the login, leave the
            // store untouched, and hand back the current cookie so this browser converges on it.
            log.debug(
                    "[REMEMBER_ME_RACE] Token for series '{}' was rotated {}ms ago by a concurrent request;"
                            + " accepting without rotating.",
                    token.getSeries(),
                    ageMs);
            setCookie(
                    new String[] {token.getSeries(), token.getTokenValue()},
                    getTokenValiditySeconds(),
                    request,
                    response);
            return getUserDetailsService().loadUserByUsername(token.getUsername());
        }

        if (token.getDate().getTime() + getTokenValiditySeconds() * 1000L < System.currentTimeMillis()) {
            throw new RememberMeAuthenticationException("Remember-me login has expired");
        }

        // Token matches: rotate the value, keeping the same series.
        PersistentRememberMeToken newToken =
                new PersistentRememberMeToken(token.getUsername(), token.getSeries(), generateTokenData(), new Date());
        try {
            tokenRepository.updateToken(newToken.getSeries(), newToken.getTokenValue(), newToken.getDate());
            setCookie(
                    new String[] {newToken.getSeries(), newToken.getTokenValue()},
                    getTokenValiditySeconds(),
                    request,
                    response);
        } catch (Exception ex) {
            log.error("Failed to update remember-me token", ex);
            throw new RememberMeAuthenticationException("Autologin failed due to data access problem");
        }
        return getUserDetailsService().loadUserByUsername(token.getUsername());
    }
}
