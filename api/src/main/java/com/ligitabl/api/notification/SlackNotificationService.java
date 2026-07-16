package com.ligitabl.api.notification;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Generic Slack notifier via an Incoming Webhook.
 *
 * Deliberately domain-agnostic — "send this string to Slack", nothing more. Domain-specific
 * message assembly lives in callers (see {@link AdminNotificationService}).
 *
 * Fire-and-forget: the HTTP call runs asynchronously on the reactor pipeline and no failure
 * (Slack error, timeout, misconfiguration) ever propagates to the caller. Error logs carry the
 * exception class only — WebClientRequestException messages embed the request URI, and the
 * webhook URL is a secret.
 */
@Service
@Slf4j
public class SlackNotificationService {

    private final WebClient webClient;
    private final String webhookUrl;
    private final boolean enabled;
    private final Duration timeout;

    public SlackNotificationService(
            @Qualifier("slackWebClient") WebClient webClient,
            @Value("${ligitabl.slack.webhook-url:}") String webhookUrl,
            @Value("${ligitabl.slack.enabled:false}") boolean enabled,
            @Value("${ligitabl.slack.timeout-seconds:5}") int timeoutSeconds) {
        this.webClient = webClient;
        this.webhookUrl = webhookUrl;
        this.enabled = enabled;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    /**
     * Post a plain-text message to the configured Slack channel. Never blocks, never throws.
     */
    @SuppressWarnings("null")
    public void send(String message) {
        if (!enabled) {
            log.debug("Slack notifications disabled; dropping message");
            return;
        }
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.warn("Slack notifications enabled but no webhook URL configured; dropping message");
            return;
        }

        try {
            webClient
                    .post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("text", message))
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(timeout)
                    .subscribe(
                            ok -> log.debug("Slack notification sent"),
                            err -> log.warn(
                                    "Slack notification failed: {}",
                                    err.getClass().getSimpleName()));
        } catch (Exception e) {
            log.warn("Slack notification failed to dispatch: {}", e.getClass().getSimpleName());
        }
    }
}
