package com.ligitabl.api.usecase.auth.passwordreset;

import java.util.UUID;

public sealed interface ResetPasswordResult {
    record Success(UUID userId) implements ResetPasswordResult {}
}
