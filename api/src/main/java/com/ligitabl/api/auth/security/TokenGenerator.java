package com.ligitabl.api.auth.security;

import java.util.Set;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.auth.Role;

public interface TokenGenerator {

    /**
     * Generate an access token.
     *
     * @param publicId the user's public ID
     * @param roles the user's roles
     * @return the generated token
     */
    String generateAccessToken(PublicId publicId, Set<Role> roles);

    /**
     * Validate a token.
     *
     * @param token the token to validate
     * @return Either error or token claims
     */
    Either<UseCaseError, TokenClaims> validateToken(String token);

    /**
     * Token claims extracted from a validated token.
     */
    record TokenClaims(PublicId publicId, Set<Role> roles) {}
}
