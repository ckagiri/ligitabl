package com.ligitabl.api.usecase.account.googlelink;

import java.util.UUID;

public sealed interface LinkGoogleError {
    record UserNotFound(UUID userId) implements LinkGoogleError {}

    record LinkedToAnotherUser(UUID otherUserId) implements LinkGoogleError {}
}
