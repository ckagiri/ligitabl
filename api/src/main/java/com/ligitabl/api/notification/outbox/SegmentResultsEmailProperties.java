package com.ligitabl.api.notification.outbox;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Recipient scoping and timing for segment-success emails.
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
