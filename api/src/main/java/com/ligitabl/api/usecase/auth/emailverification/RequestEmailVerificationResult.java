package com.ligitabl.api.usecase.auth.emailverification;

public sealed interface RequestEmailVerificationResult {
    record EmailSent(String email) implements RequestEmailVerificationResult {}
}
