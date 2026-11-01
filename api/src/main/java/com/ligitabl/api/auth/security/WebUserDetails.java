package com.ligitabl.api.auth.security;

import java.util.Collection;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class WebUserDetails implements UserDetails {

    /**
     * Pinned: this class is serialized into {@code SPRING_SESSION_ATTRIBUTES} with the stored
     * {@code SecurityContext}, and a derived value changes with any field or method edit, logging
     * every user out. Bump it only on a genuinely incompatible change.
     */
    private static final long serialVersionUID = 8102740099292411777L;

    private final UUID userId;
    private final String publicId;
    private final String email;
    private final String displayName;
    private final String password;
    private final Collection<? extends GrantedAuthority> authorities;

    public WebUserDetails(
            UUID userId,
            String publicId,
            String email,
            String displayName,
            String password,
            Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.publicId = publicId;
        this.email = email;
        this.displayName = displayName;
        this.password = password;
        this.authorities = authorities;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPublicId() {
        return publicId;
    }

    public String getEmail() {
        return email;
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

    /**
     * The stored display name, with no email fallback — null or blank when the user has not set one.
     */
    public String getRawDisplayName() {
        return displayName;
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
