package com.ligitabl.api.scheduling;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ligitabl.model.repo.PasswordResetTokenRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PasswordResetTokenCleanupJob {
    private final PasswordResetTokenRepo passwordResetTokenRepo;

    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupExpiredTokens() {
        int removed = passwordResetTokenRepo.deleteExpired();
        log.info("Password reset token cleanup removed {} expired tokens", removed);
    }
}
