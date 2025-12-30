package com.ligitabl.api.scheduling;

import com.ligitabl.api.usecases.sync.AdvanceRoundUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Round Advancement Scheduler
 *
 * Checks if the API's current matchday has advanced.
 * Updates season.currentMatchDay accordingly.
 *
 * Runs:
 * - Immediately on application startup
 * - Daily at 6:00 AM
 */
@Component
public class RoundAdvancementScheduler {

    private static final Logger log = LoggerFactory.getLogger(RoundAdvancementScheduler.class);

    private final AdvanceRoundUseCase advanceRoundUseCase;

    public RoundAdvancementScheduler(AdvanceRoundUseCase advanceRoundUseCase) {
        this.advanceRoundUseCase = advanceRoundUseCase;
    }

    /**
     * Run immediately on application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("RoundAdvancementScheduler: Running initial check on application startup");
        checkAndAdvanceRound();
    }

    /**
     * Run daily at 6:00 AM
     */
    @Scheduled(cron = "${round-advancement.cron:0 0 6 * * *}")
    public void scheduledCheck() {
        log.info("RoundAdvancementScheduler: Running scheduled daily check");
        checkAndAdvanceRound();
    }

    private void checkAndAdvanceRound() {
        try {
            var result = advanceRoundUseCase.execute(new AdvanceRoundUseCase.AdvanceRoundCommand());

            result.fold(
                    error -> {
                        log.error("Round advancement failed: {}", error);
                        return null;
                    },
                    success -> {
                        if (success.advanced()) {
                            log.info("Round advanced: matchday {} → {} (seasonId: {})",
                                    success.previousMatchday(),
                                    success.newMatchday(),
                                    success.seasonId());
                        } else {
                            log.debug("No round advancement needed: {} (matchday: {})",
                                    success.reason(),
                                    success.newMatchday());
                        }
                        return null;
                    }
            );

        } catch (Exception e) {
            log.error("Unexpected error during round advancement check", e);
        }
    }

    /**
     * For testing/manual trigger
     */
    public void triggerManualCheck() {
        log.info("Manual round advancement check triggered");
        checkAndAdvanceRound();
    }
}
