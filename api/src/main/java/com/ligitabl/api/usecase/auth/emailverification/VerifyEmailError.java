package com.ligitabl.api.usecase.auth.emailverification;

public sealed interface VerifyEmailError {
    record InvalidToken() implements VerifyEmailError {}

    record TokenExpired() implements VerifyEmailError {}

    record TokenAlreadyUsed() implements VerifyEmailError {}
}
