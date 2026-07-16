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

    public static EmailVerificationToken create(UUID userId, int validityHours) {
        Instant now = Instant.now();
        return EmailVerificationToken.builder()
                .token(UUID.randomUUID().toString())
                .userId(userId)
                .expiresAt(now.plusSeconds(validityHours * 3600L))
                .used(false)
                .createdAt(now)
                .usedAt(null)
                .build();
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !used && !isExpired();
    }

    public EmailVerificationToken markAsUsed() {
        return this.withUsed(true).withUsedAt(Instant.now());
    }
}
