package com.ligitabl.api.scheduling.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ligitabl.api.notification.outbox.JoinReminderEnqueuer;

import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs the join-reminder enqueuer once on startup, so a deploy that lands after the 9am cron
 * slot doesn't wait until the next day for that day's reminders to go out.
 *
 * <p>Safe to fire alongside the cron ({@link JoinReminderJob}): {@link JoinReminderEnqueuer}
 * skips its own run if a JOIN_REMINDER batch was already enqueued today, so whichever of the
 * two — this startup hook or the 9am cron — runs first "wins" for the day and the other is a
 * no-op.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "ligitabl.email.join-reminder.enabled", havingValue = "true")
public class JoinReminderStartupRecovery {

    private final JoinReminderEnqueuer joinReminderEnqueuer;

    @EventListener(ApplicationReadyEvent.class)
    public void runOnStartup() {
        try {
            joinReminderEnqueuer.enqueueDueReminders();
        } catch (Exception e) {
            log.error("Join reminder startup recovery failed", e);
            Sentry.captureException(e);
        }
    }
}
