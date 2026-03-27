package com.ligitabl.api.scheduling.advanceround;

import java.time.OffsetDateTime;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ligitabl.model.repo.RoundRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Round Advancement Recovery
 *
 * On startup, finds rounds that should have auto-advanced while the server was
 * down and advances them immediately.
 *
 * Example scenario:
 *   3:00 PM  Round finalized, advancement scheduled for 3:10 PM
 *   3:05 PM  Server restarts
 *   3:15 PM  Server back up — this component detects the missed 3:10 PM advancement
 *            and advances the round immediately.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RoundAdvancementRecovery {

    private final RoundRepo roundRepo;
    private final RoundAdvancementService advancementService;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverMissedAdvancements() {
        log.info("Checking for missed round advancements...");

        try {
            var missed = roundRepo.findMissedAdvancements(OffsetDateTime.now());

            if (missed.isEmpty()) {
                log.info("No missed advancements found");
                return;
            }

            log.warn("Found {} missed advancement(s), recovering...", missed.size());

            for (var round : missed) {
                try {
                    log.warn("Recovering missed advancement: roundId={} scheduledAt={}",
                            round.getId(), round.getAdvanceAt());

                    advancementService.attemptAutoAdvancement(round.getId(), round.getSeasonId());

                    log.info("Recovered advancement for roundId={}", round.getId());
                } catch (Exception e) {
                    log.error("Failed to recover advancement for roundId={}", round.getId(), e);
                }
            }

            log.info("Recovery complete: processed {} missed advancement(s)", missed.size());

        } catch (Exception e) {
            log.error("Failed to check for missed advancements", e);
        }
    }
}
