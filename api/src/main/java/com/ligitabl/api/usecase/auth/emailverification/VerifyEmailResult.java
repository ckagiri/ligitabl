package com.ligitabl.api.usecase.auth.emailverification;

import java.util.UUID;

public sealed interface VerifyEmailResult {
    record Success(UUID userId, String email) implements VerifyEmailResult {}
}
