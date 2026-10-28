package com.ligitabl.api.usecase.account.googlelink;

import java.util.UUID;

public sealed interface UnlinkGoogleError {
    record UserNotFound(UUID userId) implements UnlinkGoogleError {}

    record NotLinked(UUID userId) implements UnlinkGoogleError {}

    /** Unlinking would leave the account with no way to sign in. */
    record PasswordRequired(UUID userId) implements UnlinkGoogleError {}
}
