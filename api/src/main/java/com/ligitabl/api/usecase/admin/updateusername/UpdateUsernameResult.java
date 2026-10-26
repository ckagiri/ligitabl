package com.ligitabl.api.usecase.admin.updateusername;

import java.util.UUID;

public sealed interface UpdateUsernameResult
        permits UpdateUsernameResult.Ok,
                UpdateUsernameResult.UserNotFound,
                UpdateUsernameResult.InvalidFormat,
                UpdateUsernameResult.UsernameTaken {
    /**
     * username is the normalized value now stored, or null when cleared.
     */
    record Ok(UUID userId, String username) implements UpdateUsernameResult {}

    record UserNotFound(UUID userId) implements UpdateUsernameResult {}

    record InvalidFormat(String input) implements UpdateUsernameResult {}

    record UsernameTaken(String username) implements UpdateUsernameResult {}
}
