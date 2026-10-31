package com.ligitabl.api.usecase.admin.updateusername;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.ligitabl.model.domain.identity.User;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateUsernameUseCase {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_]{3,30}$");

    private final UserRepo userRepo;

    public UpdateUsernameResult execute(UUID userId, String rawInput) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            return new UpdateUsernameResult.UserNotFound(userId);
        }

        String normalized = rawInput == null ? "" : rawInput.trim().toLowerCase(Locale.ROOT);
        String username = normalized.isEmpty() ? null : normalized;

        if (username != null && !USERNAME_PATTERN.matcher(username).matches()) {
            return new UpdateUsernameResult.InvalidFormat(rawInput);
        }

        if (Objects.equals(username, user.getUsername())) {
            return new UpdateUsernameResult.Ok(userId, username);
        }

        if (username != null && userRepo.existsByUsername(username)) {
            return new UpdateUsernameResult.UsernameTaken(username);
        }

        try {
            userRepo.updateUsername(userId, username);
        } catch (DataAccessException e) {
            // Lost a race on the unique constraint between the exists check and the update
            log.warn("[ADMIN_UPDATE_USERNAME_CONFLICT] userId={} username={}", userId, username, e);
            return new UpdateUsernameResult.UsernameTaken(username);
        }

        log.info("[ADMIN_UPDATE_USERNAME] userId={} username={}", userId, username);
        return new UpdateUsernameResult.Ok(userId, username);
    }
}
