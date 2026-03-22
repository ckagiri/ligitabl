package com.ligitabl.model.repo;

import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.PasswordResetToken;

public interface PasswordResetTokenRepo {
    void save(PasswordResetToken token);

    Optional<PasswordResetToken> findByToken(String token);

    void invalidateAllForUser(UUID userId);

    void update(PasswordResetToken token);

    int deleteExpired();
}
