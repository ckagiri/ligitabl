package com.ligitabl.api.rest.admin.updateusername;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateUsernameUseCase {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_]{3,30}$");

    private final UserRepo userRepo;

    public sealed interface Result permits Result.Ok, Result.UserNotFound, Result.InvalidFormat, Result.UsernameTaken {
        /**
         * username is the normalized value now stored, or null when cleared.
         */
        record Ok(UUID userId, String username) implements Result {}

        record UserNotFound(UUID userId) implements Result {}

        record InvalidFormat(String input) implements Result {}

        record UsernameTaken(String username) implements Result {}
    }

    public Result execute(UUID userId, String rawInput) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            return new Result.UserNotFound(userId);
        }

        String normalized = rawInput == null ? "" : rawInput.trim().toLowerCase(Locale.ROOT);
        String username = normalized.isEmpty() ? null : normalized;

        if (username != null && !USERNAME_PATTERN.matcher(username).matches()) {
            return new Result.InvalidFormat(rawInput);
        }

        if (Objects.equals(username, user.getUsername())) {
            return new Result.Ok(userId, username);
        }

        if (username != null && userRepo.existsByUsername(username)) {
            return new Result.UsernameTaken(username);
        }

        try {
            userRepo.updateUsername(userId, username);
        } catch (DataAccessException e) {
            // Lost a race on the unique constraint between the exists check and the update
            log.warn("[ADMIN_UPDATE_USERNAME_CONFLICT] userId={} username={}", userId, username, e);
            return new Result.UsernameTaken(username);
        }

        log.info("[ADMIN_UPDATE_USERNAME] userId={} username={}", userId, username);
        return new Result.Ok(userId, username);
    }
}
