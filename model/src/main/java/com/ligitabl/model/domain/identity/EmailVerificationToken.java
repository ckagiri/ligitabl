package com.ligitabl.model.domain.identity;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;
import lombok.With;

@Value
@Builder
public class EmailVerificationToken {
    String token;
    UUID userId;
    Instant expiresAt;

    @With
    boolean used;

    Instant createdAt;

    @With
    Instant usedAt;

    /** Takes {@code now} rather than reading the clock, so the caller's {@code Clock} sets the validity window. */
    public static EmailVerificationToken create(UUID userId, int validityHours, Instant now) {
        return EmailVerificationToken.builder()
                .token(UUID.randomUUID().toString())
                .userId(userId)
                .expiresAt(now.plusSeconds(validityHours * 3600L))
                .used(false)
                .createdAt(now)
                .usedAt(null)
                .build();
    }

    /**
     * ⚠️ Strict: a token whose {@code expiresAt} is <em>exactly</em> {@code at} has not expired yet.
     * Matches {@code Season}'s windows, which are compared the same way.
     */
    public boolean isExpired(Instant at) {
        return at.isAfter(expiresAt);
    }

    public boolean isValid(Instant at) {
        return !used && !isExpired(at);
    }

    public EmailVerificationToken markAsUsed(Instant at) {
        return this.withUsed(true).withUsedAt(at);
    }
}
