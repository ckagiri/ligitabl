package com.ligitabl.api.auth.security;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.ligitabl.model.auth.Email;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;

/**
 * UserDetailsService implementation for form-based web authentication.
 * Bridges Spring Security's authentication to our domain model.
 */
@Service
@RequiredArgsConstructor
public class WebUserDetailsService implements UserDetailsService {

    private final UserRepo userRepo;

    @Override
    public UserDetails loadUserByUsername(String emailStr) throws UsernameNotFoundException {
        try {
            Email email = Email.create(emailStr);

            User user = userRepo.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

            // Convert domain roles to Spring Security authorities
            List<GrantedAuthority> authorities = user.getRoles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                    .collect(Collectors.toList());

            // Google-only accounts have no password; remember-me auto-login still loads them here.
            return new WebUserDetails(
                    user.getId(),
                    user.getPublicId().value(),
                    user.getEmail().value(),
                    user.getDisplayName(),
                    user.getPassword() == null ? "" : user.getPassword().value(),
                    authorities);
        } catch (IllegalArgumentException e) {
            // Email.create throws IllegalArgumentException for invalid emails
            throw new UsernameNotFoundException("Invalid email format: " + emailStr, e);
        }
    }
}
