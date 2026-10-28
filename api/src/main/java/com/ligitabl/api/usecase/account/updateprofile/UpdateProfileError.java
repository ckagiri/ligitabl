package com.ligitabl.api.usecase.account.updateprofile;

import java.util.UUID;

public sealed interface UpdateProfileError {
    record UserNotFound(UUID userId) implements UpdateProfileError {}
}
