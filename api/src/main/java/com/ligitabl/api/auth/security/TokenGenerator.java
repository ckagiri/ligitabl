package com.ligitabl.api.auth.security;

import java.util.Set;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.AuthenticationError;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.auth.Role;

public interface TokenGenerator {
    String generateAccessToken(PublicId publicId, Set<Role> roles);

    Either<AuthenticationError, TokenClaims> validateToken(String token);

    record TokenClaims(PublicId publicId, Set<Role> roles) {}
}
