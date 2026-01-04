package com.ligitabl.api.usecases.auth.register;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.Password;

public record RegisterCommand(
        Email email,
        @NotBlank @Size(min = 2, max = 100) String displayName,
        Password.Plaintext password) {}
