package com.ligitabl.api.notification.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/**
 * Recipient scoping for round-results emails.
 *
 * <p>{@code topN} caps recipients per round
 * {@code mode=test} sends only to ignore-list accounts so the full pipeline
 * can run locally without emailing real users.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ligitabl.email.round-results")
public class RoundResultsEmailProperties {
    private int topN = 50;
    private String mode = "live"; // live | test

    public boolean isTestMode() {
        return "test".equalsIgnoreCase(mode);
    }
}
