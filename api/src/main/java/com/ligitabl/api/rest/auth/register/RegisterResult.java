package com.ligitabl.api.rest.auth.register;

import java.util.Set;

import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.auth.Role;

public record RegisterResult(PublicId publicId, Email email, String displayName, Set<Role> roles) {}
