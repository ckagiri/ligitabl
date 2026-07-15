package com.ligitabl.api.auth.impersonation;

import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DefaultImpersonationAuthorizationService implements ImpersonationAuthorizationService {

    private final UserRepo userRepo;

    @Override
    public Result assertCanImpersonate(User original, String identifier) {
        // Defensive; @PreAuthorize("hasRole('ADMIN')") already gates the endpoint
        if (original == null || !original.hasRole(Role.ADMIN)) {
            return new Result.NotAdmin();
        }

        String trimmed = identifier == null ? "" : identifier.trim();
        if (trimmed.isEmpty()) {
            return new Result.TargetNotFound(identifier);
        }

        Optional<User> resolved = resolve(trimmed);
        if (resolved.isEmpty()) {
            return new Result.TargetNotFound(trimmed);
        }
        User target = resolved.get();

        if (target.getId().equals(original.getId())) {
            return new Result.SelfImpersonation();
        }

        // Only plain players (or role-less accounts) may be impersonated
        boolean privileged =
                target.getRoles() != null && target.getRoles().stream().anyMatch(role -> role != Role.PLAYER);
        if (privileged) {
            return new Result.TargetPrivileged(trimmed);
        }

        return new Result.Ok(target);
    }

    private Optional<User> resolve(String identifier) {
        if (identifier.contains("@")) {
            try {
                return userRepo.findByEmail(Email.create(identifier));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
        // Usernames are stored lowercase
        return userRepo.findByUsername(identifier.toLowerCase(Locale.ROOT));
    }
}
