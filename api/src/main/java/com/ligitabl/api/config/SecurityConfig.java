package com.ligitabl.api.config;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import com.ligitabl.api.auth.impersonation.ClearImpersonationLogoutHandler;
import com.ligitabl.api.auth.oauth2.CustomOAuth2UserService;
import com.ligitabl.api.auth.oauth2.OAuth2AuthenticationSuccessHandler;
import com.ligitabl.api.auth.security.JwtAuthenticationFilter;
import com.ligitabl.api.auth.security.QuietRememberMeServices;
import com.ligitabl.api.auth.security.TokenGenerator;

@Configuration
@ConditionalOnWebApplication(type = Type.SERVLET)
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${ligitabl.security.remember-me.key:dev-remember-me-key}")
    private String rememberMeKey;

    @Value("${ligitabl.security.remember-me.token-validity-seconds:1209600}")
    private int rememberMeTokenValiditySeconds;

    private static void writeApiError(
            HttpStatus status, jakarta.servlet.http.HttpServletResponse response, String message)
            throws java.io.IOException {
        if (response.isCommitted()) {
            return;
        }

        response.resetBuffer();
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
        response.flushBuffer();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(TokenGenerator tokenGenerator) {
        return new JwtAuthenticationFilter(tokenGenerator);
    }

    /**
     * Prevent Spring Boot from auto-registering this filter as a global servlet filter.
     * We only want it to run inside the API {@link SecurityFilterChain}.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter jwtAuthenticationFilter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration =
                new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Security filter chain for REST API endpoints (/api/**)
     * Uses stateless JWT authentication
     */
    @Bean
    @Order(1)
    public SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http, JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http.securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth.requestMatchers("/api/auth/**")
                        .permitAll()
                        .requestMatchers("/api/me")
                        .authenticated()
                        .requestMatchers("/api/admin", "/api/admin/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/api/player", "/api/player/**")
                        .hasRole("PLAYER")
                        .anyRequest()
                        .permitAll())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                            // Keep clients happy with a Basic challenge, but don't redirect to web login.
                            response.setHeader("WWW-Authenticate", "Basic realm=\"LigiTabl\"");
                            writeApiError(HttpStatus.UNAUTHORIZED, response, "Unauthorized");
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeApiError(HttpStatus.FORBIDDEN, response, "Forbidden")))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Server-side store for remember-me tokens ({@code persistent_logins} table).
     * Tokens don't involve the user's password, are individually revocable, and
     * stolen-cookie reuse is detected — which also makes them safe for
     * password-less Google-only accounts.
     */
    @Bean
    public PersistentTokenRepository persistentTokenRepository(javax.sql.DataSource dataSource) {
        var repository = new JdbcTokenRepositoryImpl();
        repository.setDataSource(dataSource);
        return repository;
    }

    /**
     * Remember-me for form login and registration: opt-in via the {@code rememberMe}
     * checkbox bound on {@code LoginForm}/{@code RegisterForm}. Wrapped in
     * {@link QuietRememberMeServices} — this is the instance wired into
     * {@code RememberMeAuthenticationFilter} below, i.e. the one whose
     * {@code autoLogin} runs on every request, so it's the one that needs
     * protection against a stale cookie surfacing as an uncaught exception.
     */
    @Bean
    public RememberMeServices rememberMeServices(
            @Qualifier("webUserDetailsService") UserDetailsService userDetailsService,
            PersistentTokenRepository persistentTokenRepository) {
        var services = new PersistentTokenBasedRememberMeServices(
                rememberMeKey, userDetailsService, persistentTokenRepository);
        services.setParameter("rememberMe");
        services.setTokenValiditySeconds(rememberMeTokenValiditySeconds);
        return new QuietRememberMeServices(services);
    }

    /**
     * Remember-me for OAuth2 logins: always-on, because the redirect flow has no
     * checkbox to carry the user's choice. Shares the key and token store with
     * {@link #rememberMeServices}, so the same filter validates both cookies.
     * Injected by bean name into {@code OAuth2AuthenticationSuccessHandler}.
     */
    @Bean
    public RememberMeServices oauth2RememberMeServices(
            @Qualifier("webUserDetailsService") UserDetailsService userDetailsService,
            PersistentTokenRepository persistentTokenRepository) {
        var services = new PersistentTokenBasedRememberMeServices(
                rememberMeKey, userDetailsService, persistentTokenRepository);
        services.setAlwaysRemember(true);
        services.setTokenValiditySeconds(rememberMeTokenValiditySeconds);
        return services;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("webUserDetailsService") UserDetailsService userDetailsService,
            RememberMeServices rememberMeServices,
            CustomOAuth2UserService customOAuth2UserService,
            OAuth2AuthenticationSuccessHandler oauth2AuthenticationSuccessHandler,
            ClearImpersonationLogoutHandler clearImpersonationLogoutHandler)
            throws Exception {
        http.csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/seasonprediction",
                        "/seasonprediction/**",
                        "/leaderboard/user/modal",
                        "/auth/login",
                        "/auth/login/process",
                        "/auth/register",
                        "/auth/forgot-password",
                        "/auth/reset-password")) // Allow HTMX + auth forms without CSRF
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/",
                                "/home",
                                "/my-table",
                                "/my-table/**",
                                "/my-table/guest",
                                "/my-table/guest/**",
                                "/auth/login",
                                "/auth/register",
                                "/auth/forgot-password",
                                "/auth/reset-password",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/leaderboard",
                                "/leaderboard/**",
                                "/standings",
                                "/matches",
                                "/rounds/**",
                                "/error",
                                "/css/**",
                                "/dist/**",
                                "/js/**",
                                "/images/**",
                                "/webjars/**",
                                "/favicon.ico",
                                "/favicon.svg",
                                "/apple-touch-icon.png")
                        .permitAll()
                        .requestMatchers("/predictions/user/me")
                        .authenticated()
                        .requestMatchers("/predictions/user/guest", "/predictions/user/guest/*")
                        .permitAll()
                        .requestMatchers("/seasonprediction/**")
                        .hasRole("PLAYER")
                        .requestMatchers("/contests/**")
                        .authenticated()
                        .requestMatchers("/profile/**")
                        .authenticated()
                        .anyRequest()
                        .permitAll())
                .formLogin(form -> form.loginPage("/auth/login")
                        .loginProcessingUrl("/auth/login/process")
                        .defaultSuccessUrl("/my-table", false)
                        .permitAll())
                .oauth2Login(oauth2 -> oauth2.loginPage("/auth/login")
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oauth2AuthenticationSuccessHandler))
                .rememberMe(remember -> remember.rememberMeServices(rememberMeServices))
                .logout(logout -> logout.logoutUrl("/auth/logout")
                        .addLogoutHandler(clearImpersonationLogoutHandler)
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID", "remember-me")
                        .permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

        return http.build();
    }

    /**
     * Authentication provider for form login
     * Connects UserDetailsService with PasswordEncoder
     *
     * Note: Using static factory method instead of deprecated constructors.
     * The PasswordEncoder is automatically injected by Spring.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            @Qualifier("webUserDetailsService") UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    /**
     * Authentication manager for manual authentication (e.g., during registration)
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
