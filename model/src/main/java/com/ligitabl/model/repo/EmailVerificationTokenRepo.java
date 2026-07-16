package com.ligitabl.model.repo;

import java.util.Optional;
import java.util.UUID;

import com.ligitabl.model.domain.EmailVerificationToken;

public interface EmailVerificationTokenRepo {
    void save(EmailVerificationToken token);

    Optional<EmailVerificationToken> findByToken(String token);

    /**
     * Newest token for the user by creation time, used or not.
     * Backs the resend cooldown check.
     */
    Optional<EmailVerificationToken> findLatestForUser(UUID userId);

    void invalidateAllForUser(UUID userId);

    void update(EmailVerificationToken token);

    int deleteExpired();

    void deleteAllForUser(UUID userId);
}
