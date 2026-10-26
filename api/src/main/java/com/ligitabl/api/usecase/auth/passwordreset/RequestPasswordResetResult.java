package com.ligitabl.api.usecase.auth.passwordreset;

public sealed interface RequestPasswordResetResult {
    record EmailSent(String email) implements RequestPasswordResetResult {}
}
