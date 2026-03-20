package com.ligitabl.model.domain;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;
import lombok.Value;
import lombok.With;

@Value
@Builder
public class PasswordResetToken {
    String token;
    UUID userId;
    Instant expiresAt;
    @With
    boolean used;
    Instant createdAt;
    @With
    Instant usedAt;

    public static PasswordResetToken create(UUID userId, int validityMinutes) {
        Instant now = Instant.now();
        return PasswordResetToken.builder()
                .token(UUID.randomUUID().toString())
                .userId(userId)
                .expiresAt(now.plusSeconds(validityMinutes * 60L))
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

    public PasswordResetToken markAsUsed() {
        return this.withUsed(true).withUsedAt(Instant.now());
    }
}
