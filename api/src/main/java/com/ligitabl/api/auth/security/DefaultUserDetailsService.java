package com.ligitabl.api.auth.security;

import static org.springframework.security.core.userdetails.User.withUsername;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import com.ligitabl.model.auth.Email;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DefaultUserDetailsService implements UserDetailsService {

    private final UserRepo userRepo;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Email email;
        try {
            email = Email.create(username);
        } catch (RuntimeException ex) {
            throw new UsernameNotFoundException("Invalid username", ex);
        }

        User user = userRepo.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("User not found"));

        List<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role.getValue()))
                .toList();

        // Important: return publicId as the principal name, so controllers can use Authentication#getName()
        // consistently (JWT and Basic Auth both resolve to publicId).
        return withUsername(user.getPublicId().value())
                .password(user.getPassword().value())
                .authorities(authorities)
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }
}
