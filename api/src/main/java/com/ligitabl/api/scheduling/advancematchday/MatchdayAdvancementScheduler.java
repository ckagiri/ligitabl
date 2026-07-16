package com.ligitabl.api.scheduling.advancematchday;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ligitabl.api.notification.AdminNotificationService;

import io.sentry.Sentry;

/**
 * Matchday Advancement Scheduler
 *
 * Checks if the API's current matchday has advanced.
 * Updates season.currentMatchDay accordingly.
 *
 * Runs:
 * - Immediately on application startup
 * - Daily at 6:00 AM
 */
@Component
@ConditionalOnProperty(name = "ligitabl.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class MatchdayAdvancementScheduler {

    private static final Logger log = LoggerFactory.getLogger(MatchdayAdvancementScheduler.class);

    private final AdvanceMatchdayUseCase advanceMatchdayUseCase;
    private final AdminNotificationService adminNotificationService;

    public MatchdayAdvancementScheduler(
            AdvanceMatchdayUseCase advanceMatchdayUseCase, AdminNotificationService adminNotificationService) {
        this.advanceMatchdayUseCase = advanceMatchdayUseCase;
        this.adminNotificationService = adminNotificationService;
    }

    /**
     * Run immediately on application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("MatchdayAdvancementScheduler: Running initial check on application startup");
        checkAndAdvanceMatchday();
    }

    /**
     * Run daily at 6:00 AM
     */
    @Scheduled(cron = "${round-advancement.cron:0 0 6 * * *}")
    public void scheduledCheck() {
        log.info("MatchdayAdvancementScheduler: Running scheduled daily check");
        checkAndAdvanceMatchday();
    }

    private void checkAndAdvanceMatchday() {
        try {
            var result = advanceMatchdayUseCase.execute(new AdvanceMatchdayUseCase.AdvanceMatchdayCommand());

            result.fold(
                    error -> {
                        log.error("Matchday advancement failed: {}", error);
                        return null;
                    },
                    success -> {
                        if (success.advanced()) {
                            log.info(
                                    "Matchday advanced: matchday {} → {} (seasonId: {})",
                                    success.previousMatchday(),
                                    success.newMatchday(),
                                    success.seasonId());
                            adminNotificationService.notifyMatchdayAdvanced(
                                    success.previousMatchday(), success.newMatchday(), success.seasonId());
                        } else {
                            log.info(
                                    "No round advancement needed: {} (matchday: {})",
                                    success.reason(),
                                    success.newMatchday());
                        }
                        return null;
                    });

        } catch (Exception e) {
            log.error("Unexpected error during round advancement check", e);
            Sentry.captureException(e);
        }
    }

    /**
     * For testing/manual trigger
     */
    public void triggerManualCheck() {
        log.info("Manual matchday advancement check triggered");
        checkAndAdvanceMatchday();
    }
}
