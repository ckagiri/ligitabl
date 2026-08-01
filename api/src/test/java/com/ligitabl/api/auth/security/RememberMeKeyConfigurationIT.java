package com.ligitabl.api.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter;
import org.springframework.test.util.ReflectionTestUtils;

import com.ligitabl.api.testsupport.AbstractPostgresIT;

/**
 * Guards the remember-me key wiring in {@code SecurityConfig}.
 *
 * <p><b>The bug this exists to catch.</b> {@code RememberMeConfigurer} can only infer the
 * key from the services bean when that bean is an {@code AbstractRememberMeServices}. Ours
 * is wrapped in {@link QuietRememberMeServices}, so without an explicit {@code .key(...)}
 * the configurer silently falls back to {@code UUID.randomUUID()} — a different key on
 * every startup. Auto-login then always fails {@code RememberMeAuthenticationProvider}'s
 * key-hash check, and {@code RememberMeAuthenticationFilter} responds by calling
 * {@code loginFail()}, cancelling the user's cookie. The symptom is remember-me never
 * working on any device, with no error surfaced anywhere.
 *
 * <p>Nothing else in the suite catches it: the services themselves behave correctly in
 * isolation, and the breakage only exists in the assembled filter chain.
 */
@SpringBootTest
class RememberMeKeyConfigurationIT extends AbstractPostgresIT {

    @Value("${ligitabl.security.remember-me.key:dev-remember-me-key}")
    String configuredKey;

    @Autowired
    List<SecurityFilterChain> filterChains;

    @Test
    @DisplayName("The remember-me provider accepts tokens signed with the configured key")
    void rememberMeProvider_usesConfiguredKey() {
        AuthenticationManager authenticationManager = rememberMeAuthenticationManager();

        var token = new RememberMeAuthenticationToken(
                configuredKey, "someone@example.com", List.of(new SimpleGrantedAuthority("ROLE_PLAYER")));

        // Fails with BadCredentialsException("...does not contain the expected key") if the
        // configurer fell back to a random key.
        assertThatCode(() -> authenticationManager.authenticate(token)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A token signed with any other key is still rejected")
    void rememberMeProvider_rejectsForeignKey() {
        AuthenticationManager authenticationManager = rememberMeAuthenticationManager();

        var foreign = new RememberMeAuthenticationToken(
                UUID.randomUUID().toString(), "someone@example.com", List.of(new SimpleGrantedAuthority("ROLE_PLAYER")));

        assertThatCode(() -> authenticationManager.authenticate(foreign)).isInstanceOf(RuntimeException.class);
    }

    /** The AuthenticationManager the web chain's RememberMeAuthenticationFilter actually delegates to. */
    private AuthenticationManager rememberMeAuthenticationManager() {
        var filter = filterChains.stream()
                .flatMap(chain -> chain.getFilters().stream())
                .filter(RememberMeAuthenticationFilter.class::isInstance)
                .map(RememberMeAuthenticationFilter.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No RememberMeAuthenticationFilter in any SecurityFilterChain"));

        var manager = ReflectionTestUtils.getField(filter, "authenticationManager");
        assertThat(manager).isInstanceOf(AuthenticationManager.class);
        return (AuthenticationManager) manager;
    }
}
