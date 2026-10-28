package com.ligitabl.api.usecase.account.setpassword;

import java.util.UUID;

public sealed interface SetPasswordError {
    record UserNotFound(UUID userId) implements SetPasswordError {}

    /** Changing an existing password is a different flow (reset); this only fills a missing one. */
    record AlreadySet(UUID userId) implements SetPasswordError {}

    record InvalidPassword(String reason) implements SetPasswordError {}
}
