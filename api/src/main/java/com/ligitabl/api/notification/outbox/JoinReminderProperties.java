package com.ligitabl.api.notification.outbox;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Backoff schedule for join-reminder emails.
 *
 * <p>{@code stageDays} must be ascending; a user is emailed once per stage, at
 * the latest stage their signup age has reached, never more than one stage
 * per run — see {@link JoinReminderEnqueuer}.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ligitabl.email.join-reminder")
public class JoinReminderProperties {
    private boolean enabled = false;
    private List<Integer> stageDays = List.of(1, 4, 11);
}
