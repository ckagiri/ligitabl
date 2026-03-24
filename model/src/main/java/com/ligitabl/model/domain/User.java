package com.ligitabl.model.domain;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.Password;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.auth.Role;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.With;

/**
 * Immutable by design (using @Value from Lombok).
 * Changes create new instances (using @With).
 */
@Value
@Builder
@AllArgsConstructor(access = AccessLevel.PUBLIC)
public class User {

    /**
     * Internal UUID - never exposed outside the bounded context
     */
    UUID id;

    /**
     * Public identifier - used in APIs and external communication
     */
    PublicId publicId;

    Email email;

    @With
    String displayName;

    @With
    Password.Hashed password;

    @With
    Set<Role> roles;

    @With
    boolean emailVerified;

    @With
    String googleId;

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean hasAnyRole(Role... requiredRoles) {
        return Set.of(requiredRoles).stream().anyMatch(roles::contains);
    }

    public boolean hasAllRoles(Role... requiredRoles) {
        return roles.containsAll(Set.of(requiredRoles));
    }

    /**
     * Add a role to the user.
     * Returns a new User instance with the added role.
     */
    public User addRole(Role role) {
        Set<Role> newRoles = new HashSet<>(roles);
        newRoles.add(role);
        return withRoles(Set.copyOf(newRoles));
    }

    /**
     * Remove a role from the user.
     * Returns a new User instance without the role.
     */
    public User removeRole(Role role) {
        Set<Role> newRoles = new HashSet<>(roles);
        newRoles.remove(role);
        return withRoles(Set.copyOf(newRoles));
    }
}
