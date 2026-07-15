package com.ligitabl.api.auth.impersonation;

import java.util.Set;
import java.util.UUID;

import com.ligitabl.model.auth.Role;
import com.ligitabl.model.domain.User;

public record UserSummary(UUID id, String email, String displayName, Set<Role> roles) {

    public static UserSummary from(User user) {
        return new UserSummary(
                user.getId(),
                user.getEmail() == null ? null : user.getEmail().value(),
                user.getDisplayName(),
                user.getRoles() == null ? Set.of() : user.getRoles());
    }
}
