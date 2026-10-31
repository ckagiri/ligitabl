package com.ligitabl.api.usecase.auth.getcurrentuser;

import java.util.Set;

import com.ligitabl.model.domain.identity.Email;
import com.ligitabl.model.domain.identity.PublicId;
import com.ligitabl.model.domain.identity.Role;

public record UserInfo(PublicId publicId, Email email, String displayName, Set<Role> roles, boolean emailVerified) {}
