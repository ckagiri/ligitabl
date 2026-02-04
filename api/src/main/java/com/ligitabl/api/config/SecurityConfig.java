package com.ligitabl.api.config;

import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.ligitabl.api.auth.security.JwtAuthenticationFilter;
import com.ligitabl.api.auth.security.TokenGenerator;

@Configuration
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
     * Security filter chain for web UI endpoints
     * Uses session-based form login authentication
     */
    @Bean
    @Order(2)
        public SecurityFilterChain webSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("webUserDetailsService") UserDetailsService userDetailsService) throws Exception {
        http.csrf(csrf -> csrf.ignoringRequestMatchers(
            "/seasonprediction",
            "/seasonprediction/**",
            "/auth/login",
            "/auth/register")) // Allow HTMX + auth forms without CSRF
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/",
                                "/auth/login",
                                "/auth/register",
                                "/leaderboard",
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
                        .hasRole("PLAYER")
                        .requestMatchers("/predictions/user/guest", "/predictions/user/guest/*")
                        .permitAll()
                        .requestMatchers("/predictions/user/*")
                        .permitAll()
                        .requestMatchers("/seasonprediction/**")
                        .hasRole("PLAYER")
                        .anyRequest()
                        .authenticated())
                .formLogin(form -> form.loginPage("/auth/login")
                    .loginProcessingUrl("/auth/login/process")
                    .defaultSuccessUrl("/predictions/user/me", true)
                    .permitAll())
                .rememberMe(remember -> remember
                    .key(rememberMeKey)
                    .rememberMeParameter("remember-me")
                    .tokenValiditySeconds(rememberMeTokenValiditySeconds)
                    .userDetailsService(userDetailsService))
                .logout(logout -> logout.logoutUrl("/auth/logout")
                    .logoutSuccessUrl("/")
                    .invalidateHttpSession(true)
                    .deleteCookies("JSESSIONID", "remember-me")
                    .permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
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
