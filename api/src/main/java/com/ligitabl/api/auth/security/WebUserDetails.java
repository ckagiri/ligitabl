package com.ligitabl.api.auth.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * Custom UserDetails implementation for web authentication.
 * Extends Spring Security's UserDetails to include displayName.
 */
public class WebUserDetails implements UserDetails {

    private final String email;
    private final String displayName;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;

    public WebUserDetails(
            String email,
            String displayName,
            String password,
            Collection<? extends GrantedAuthority> authorities) {
        this.email = email;
        this.displayName = displayName;
        this.password = password;
        this.authorities = authorities;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    /**
     * Get the display name to show in the UI.
     * Returns displayName if available, otherwise falls back to email.
     */
    public String getDisplayName() {
        return (displayName != null && !displayName.isBlank()) ? displayName : email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
