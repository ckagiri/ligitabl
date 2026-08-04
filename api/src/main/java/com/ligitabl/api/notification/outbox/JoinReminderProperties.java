package com.ligitabl.api.notification.outbox;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Backoff schedule for join-reminder emails.
 *
 * <p>{@code stageDays} must be ascending; a user is emailed once per stage, at
 * the latest stage their time-since-last-seen has reached, never more than one
 * stage per run — see {@link JoinReminderEnqueuer}.
 *
 * <p>{@code quietDaysBeforePredictionsOpen} suppresses runs in the run-up to predictions
 * opening, when auto-join is about to hand these same users a table: "you haven't set your
 * table" followed hours later by "welcome, your table is set" reads as a bug.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ligitabl.email.join-reminder")
public class JoinReminderProperties {
    private boolean enabled = false;
    private List<Integer> stageDays = List.of(1, 4, 11);
    private int quietDaysBeforePredictionsOpen = 1;
}
