package com.ligitabl.api.scheduling.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ligitabl.api.usecase.notification.joinreminder.EnqueueJoinRemindersUseCase;

import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs the join-reminder enqueuer once on startup, so a deploy after the cron slot doesn't hold
 * that day's reminders until tomorrow. Safe alongside {@link JoinReminderJob}:
 * {@link EnqueueJoinRemindersUseCase} skips the run if a JOIN_REMINDER batch was already enqueued
 * today.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "ligitabl.email.join-reminder.enabled", havingValue = "true")
public class JoinReminderStartupRecovery {

    private final EnqueueJoinRemindersUseCase enqueueJoinRemindersUseCase;

    @EventListener(ApplicationReadyEvent.class)
    public void runOnStartup() {
        try {
            enqueueJoinRemindersUseCase.execute();
        } catch (Exception e) {
            log.error("Join reminder startup recovery failed", e);
            Sentry.captureException(e);
        }
    }
}
