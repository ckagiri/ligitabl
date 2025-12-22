package com.ligitabl.api.auth.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.auth.Role;

@Component
public class JwtTokenGenerator implements TokenGenerator {

    private final SecretKey secretKey;
    private final long expirationTime;

    public JwtTokenGenerator(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long expirationTime) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationTime = expirationTime;
    }

    @Override
    public String generateAccessToken(PublicId publicId, Set<Role> roles) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationTime);

        List<String> roleStrings = roles.stream().map(Role::getValue).collect(Collectors.toList());

        return Jwts.builder()
                .subject(publicId.value())
                .claim("roles", roleStrings)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    @Override
    public Either<UseCaseError, TokenClaims> validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String publicIdStr = claims.getSubject();

            return Either.catching(() -> PublicId.create(publicIdStr), UseCaseErrors::fromException)
                    .flatMap(publicId -> {
                        @SuppressWarnings("unchecked")
                        List<String> roleStrings = claims.get("roles", List.class);

                        Set<Role> roles = (roleStrings == null ? List.<String>of() : roleStrings)
                                .stream()
                                .map(roleStr -> Either.catching(() -> Role.fromString(roleStr)))
                                .filter(either -> either.isRight())
                                .map(either -> either.get())
                                .collect(Collectors.toSet());

                        return Either.right(new TokenClaims(publicId, roles));
                    });

        } catch (Exception e) {
            return Either.left(UseCaseErrors.unauthorized("Unable to parse token"));
        }
    }
}
