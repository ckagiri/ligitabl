package com.ligitabl.api.usecase.auth.emailverification;

public sealed interface RequestEmailVerificationError {
    record UserNotFound() implements RequestEmailVerificationError {}

    record AlreadyVerified() implements RequestEmailVerificationError {}

    record ResendTooSoon() implements RequestEmailVerificationError {}

    record UnexpectedError() implements RequestEmailVerificationError {}
}
