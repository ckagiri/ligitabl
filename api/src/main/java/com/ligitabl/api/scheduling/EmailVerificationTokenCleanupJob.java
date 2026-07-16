package com.ligitabl.api.scheduling;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ligitabl.model.repo.EmailVerificationTokenRepo;

import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationTokenCleanupJob {
    private final EmailVerificationTokenRepo emailVerificationTokenRepo;

    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupExpiredTokens() {
        try {
            int removed = emailVerificationTokenRepo.deleteExpired();
            log.info("Email verification token cleanup removed {} expired tokens", removed);
        } catch (Exception e) {
            log.error("Email verification token cleanup failed", e);
            Sentry.captureException(e);
        }
    }
}
