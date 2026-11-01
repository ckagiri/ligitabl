package com.ligitabl.api.usecase.notification.joinreminder;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Backoff schedule for join-reminder emails.
 *
 * <p>{@code stageDays} must be ascending. A user gets at most one email per run, at the latest stage
 * their time since last seen has reached; see {@link EnqueueJoinRemindersUseCase}.
 * {@code quietDaysBeforePredictionsOpen} suppresses runs just before predictions open, when
 * auto-join is about to give these users a table.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ligitabl.email.join-reminder")
public class JoinReminderProperties {
    private boolean enabled = false;
    private List<Integer> stageDays = List.of(1, 4, 11);
    private int quietDaysBeforePredictionsOpen = 1;
}
