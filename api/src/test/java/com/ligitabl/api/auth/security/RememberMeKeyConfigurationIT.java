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
 * Guards the remember-me key wiring in {@code SecurityConfig}. Without an explicit
 * {@code .key(...)}, {@code RememberMeConfigurer} can't infer the key through
 * {@link QuietRememberMeServices} and picks a random one per startup, so every auto-login fails
 * the provider's key-hash check and the cookie is cancelled, with no error anywhere.
 *
 * <p>Only the assembled filter chain shows this; the services behave correctly in isolation.
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
                UUID.randomUUID().toString(),
                "someone@example.com",
                List.of(new SimpleGrantedAuthority("ROLE_PLAYER")));

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
