package com.ligitabl.api.notification.outbox;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Recipient scoping and timing for segment-success emails.
 *
 * <p>{@code topN} is the size of the podium, not a mailing-list cap — unlike
 * {@link RoundResultsEmailProperties#getTopN()}, a filtered-out finisher's slot is <em>not</em>
 * backfilled from the next rank down (see {@code SegmentResultsEmailEnqueuer}).
 *
 * <p>{@code mode=test} sends only to ignore-list accounts, sharing
 * {@code round_results_email_ignore_list} rather than keeping a second list in sync.
 *
 * <p>{@code delay} holds a round-boundary event back so the podium email doesn't arrive in the same
 * minute as the round-results email for the same boundary. {@code PT0S} makes the whole chain run
 * in one relay poll, which is what local verification and the ITs use.
 *
 * <p>{@code seasonDelay} is much shorter because the season finale has nothing to compete with:
 * completing the season is a deliberate admin action taken well after the last round advanced, so
 * its round-results email is long gone. Waiting a full day would just make the result feel stale.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ligitabl.email.segment-results")
public class SegmentResultsEmailProperties {
    private int topN = 3;
    private String mode = "live"; // live | test
    private Duration delay = Duration.ofDays(1);
    private Duration seasonDelay = Duration.ofHours(1);

    public boolean isTestMode() {
        return "test".equalsIgnoreCase(mode);
    }
}
