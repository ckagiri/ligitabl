package com.ligitabl.api.usecase.auth.passwordreset;

public sealed interface RequestPasswordResetError {
    record UnexpectedError() implements RequestPasswordResetError {}
}
