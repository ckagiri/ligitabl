package com.ligitabl.api.auth.security;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * JWT authentication filter.
 * Extracts and validates JWT tokens using the TokenGenerator port.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final TokenGenerator tokenGenerator;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            // Railway Oriented Programming: validate token
            tokenGenerator
                    .validateToken(token)
                    .peek(claims -> {
                        // Success path: set authentication
                        List<SimpleGrantedAuthority> authorities = claims.roles().stream()
                                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getValue()))
                                .toList();

                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                claims.publicId().value(), null, authorities);

                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        log.debug("JWT authentication successful for: {}", claims.publicId());
                    })
                    .peekLeft(error -> {
                        // Error path: log the error
                        log.debug("JWT authentication failed: {}", error.getMessage());
                    });
        }

        filterChain.doFilter(request, response);
    }
}
