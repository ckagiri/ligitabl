package com.ligitabl.api.usecases.auth.getcurrentuser;

import java.util.Set;

import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.auth.Role;

public record UserInfo(PublicId publicId, Email email, String displayName, Set<Role> roles, boolean emailVerified) {}
