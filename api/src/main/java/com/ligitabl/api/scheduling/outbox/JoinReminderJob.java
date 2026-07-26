package com.ligitabl.api.scheduling.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ligitabl.api.notification.outbox.JoinReminderEnqueuer;

import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "ligitabl.email.join-reminder.enabled", havingValue = "true")
public class JoinReminderJob {

    private final JoinReminderEnqueuer joinReminderEnqueuer;

    @Scheduled(cron = "${ligitabl.email.join-reminder.cron:0 0 9 * * *}")
    public void run() {
        try {
            joinReminderEnqueuer.enqueueDueReminders();
        } catch (Exception e) {
            log.error("Join reminder job failed", e);
            Sentry.captureException(e);
        }
    }
}
