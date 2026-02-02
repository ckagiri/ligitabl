package com.ligitabl.api.rest.auth.login;

import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.Password;

public record LoginCommand(Email email, Password.Plaintext password) {}
