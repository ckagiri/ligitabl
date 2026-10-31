package com.ligitabl.api.usecase.auth.register;

import com.ligitabl.model.domain.identity.Email;
import com.ligitabl.model.domain.identity.Password;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterCommand(
        Email email, @NotBlank @Size(min = 2, max = 100) String displayName, Password.Plaintext password) {}
