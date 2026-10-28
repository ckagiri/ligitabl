package com.ligitabl.api.usecase.auth.login;

public sealed interface VerifyCredentialsError {
    /** One answer for unknown email, wrong password and no password, so callers can't tell which. */
    record InvalidCredentials() implements VerifyCredentialsError {}
}
