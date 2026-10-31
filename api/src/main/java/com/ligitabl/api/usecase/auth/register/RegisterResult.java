package com.ligitabl.api.usecase.auth.register;

import java.util.Set;

import com.ligitabl.model.domain.identity.Email;
import com.ligitabl.model.domain.identity.PublicId;
import com.ligitabl.model.domain.identity.Role;

public record RegisterResult(PublicId publicId, Email email, String displayName, Set<Role> roles) {}
