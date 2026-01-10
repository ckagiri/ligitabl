package com.ligitabl.api.auth.security;

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

import java.util.List;
import java.util.stream.Collectors;

/**
 * UserDetailsService implementation for form-based web authentication.
 * Bridges Spring Security's authentication to our domain model.
 */
@Service
@RequiredArgsConstructor
public class WebUserDetailsService implements UserDetailsService {

    private final UserRepo userRepo;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        try {
            // Convert email string to domain Email type
            Email emailObj = Email.create(email);

            // Fetch user from repository
            User user = userRepo
                    .findByEmail(emailObj)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

            // Convert domain roles to Spring Security authorities
            List<GrantedAuthority> authorities = user.getRoles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                    .collect(Collectors.toList());

            // Return Spring Security UserDetails
            return new org.springframework.security.core.userdetails.User(
                    user.getEmail().value(), user.getPassword().value(), authorities);
        } catch (IllegalArgumentException e) {
            // Email.create throws IllegalArgumentException for invalid emails
            throw new UsernameNotFoundException("Invalid email format: " + email);
        }
    }
}
