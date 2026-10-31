package com.ligitabl.api.usecase.auth.login;

import com.ligitabl.model.domain.identity.Email;
import com.ligitabl.model.domain.identity.Password;

public record LoginCommand(Email email, Password.Plaintext password) {}
