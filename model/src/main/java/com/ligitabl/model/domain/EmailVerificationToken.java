package com.ligitabl.model.domain;

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

    /**
     * The instant is an argument rather than a wall-clock read so the caller's {@code Clock} decides
     * it — the same reason {@link Season#getSeasonState(Instant)} takes one. A token minted from a
     * different instant than {@link #isExpired(Instant)} later evaluates against describes a
     * validity window nobody chose.
     */
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
