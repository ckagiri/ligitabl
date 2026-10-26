package com.ligitabl.api.usecase.auth.passwordreset;

public sealed interface ResetPasswordError {
    record InvalidToken() implements ResetPasswordError {}

    record TokenExpired() implements ResetPasswordError {}

    record TokenAlreadyUsed() implements ResetPasswordError {}

    record WeakPassword(String reason) implements ResetPasswordError {}
}
