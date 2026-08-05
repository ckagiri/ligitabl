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
 * <p>{@code delay} holds each event back so the podium email doesn't arrive in the same minute as
 * the round-results email for the same boundary. {@code PT0S} makes the whole chain run in one
 * relay poll, which is what local verification and the ITs use.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ligitabl.email.segment-results")
public class SegmentResultsEmailProperties {
    private int topN = 3;
    private String mode = "live"; // live | test
    private Duration delay = Duration.ofDays(1);

    public boolean isTestMode() {
        return "test".equalsIgnoreCase(mode);
    }
}
