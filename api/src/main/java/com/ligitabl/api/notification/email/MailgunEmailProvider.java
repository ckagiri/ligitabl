package com.ligitabl.api.notification.email;

import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.ligitabl.api.shared.Either;

import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnProperty(name = "ligitabl.email.provider", havingValue = "mailgun")
@Slf4j
public class MailgunEmailProvider implements EmailProvider {

    /** Mailgun's non-standard "account sending limit exhausted" status. */
    private static final int MAILGUN_RATE_LIMIT_STATUS = 420;

    private static final Pattern RETRY_AFTER_PATTERN = Pattern.compile("try again after ([^\"\\\\\\r\\n]+)");

    private final String apiKey;
    private final String domain;
    private final String fromEmail;
    private final String fromName;
    private final String apiBaseUrl;
    private final RestTemplate restTemplate;

    public MailgunEmailProvider(
            @Value("${ligitabl.email.mailgun.api-key}") String apiKey,
            @Value("${ligitabl.email.mailgun.domain}") String domain,
            @Value("${ligitabl.email.from-email}") String fromEmail,
            @Value("${ligitabl.email.from-name:LigiPredictor}") String fromName,
            @Value("${ligitabl.email.mailgun.api-base-url:https://api.mailgun.net/v3}") String apiBaseUrl) {
        this.apiKey = apiKey;
        this.domain = domain;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.apiBaseUrl = apiBaseUrl;
        this.restTemplate = new RestTemplate();

        log.info("[MAILGUN_PROVIDER_INITIALIZED] domain={} fromEmail={}", domain, fromEmail);
    }

    @Override
    @SuppressWarnings("null")
    public Either<EmailError, Void> sendBatch(
            List<String> recipientEmails, EmailContent content, EmailCommand.Priority priority) {

        log.info("[MAILGUN_SEND_BATCH] recipients={} subject={}", recipientEmails.size(), content.subject());

        try {
            URI uri = URI.create(String.format("%s/%s/messages", apiBaseUrl, domain));

            String encodedAuth = Base64.getEncoder().encodeToString(("api:" + apiKey).getBytes());

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("from", String.format("%s <%s>", fromName, fromEmail));
            formData.add("subject", content.subject());
            formData.add("html", content.htmlBody());

            // Sending both parts makes the message multipart/alternative. HTML-only mail is a
            // negative deliverability signal and leaves text-only and accessibility clients with
            // whatever they can salvage from the markup.
            if (content.textBody() != null && !content.textBody().isBlank()) {
                formData.add("text", content.textBody());
            }

            for (String recipient : recipientEmails) {
                formData.add("to", recipient);
            }

            if (priority == EmailCommand.Priority.HIGH) {
                formData.add("o:tag", "high-priority");
            }

            RequestEntity<MultiValueMap<String, String>> request = RequestEntity.post(uri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth)
                    .body(formData);

            ResponseEntity<String> response = restTemplate.exchange(request, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[MAILGUN_SEND_SUCCESS] recipients={}", recipientEmails.size());
                return Either.right(null);
            } else {
                log.error("[MAILGUN_SEND_ERROR] status={} body={}", response.getStatusCode(), response.getBody());
                return Either.left(
                        new EmailError.EmailProviderError("Mailgun send failed: " + response.getStatusCode()));
            }

        } catch (HttpStatusCodeException e) {
            // 420 is Mailgun's account-limit refusal (recipient/message quota for the plan and
            // domain-verification state). The body carries the reset time; surfacing it as
            // RateLimited lets the caller wait out a window far longer than any retry backoff.
            if (e.getStatusCode().value() == MAILGUN_RATE_LIMIT_STATUS) {
                String body = e.getResponseBodyAsString();
                Instant retryAfter = parseRetryAfter(body);
                log.warn(
                        "[MAILGUN_RATE_LIMITED] recipients={} retryAfter={} body={}",
                        recipientEmails.size(),
                        retryAfter,
                        body);
                return Either.left(new EmailError.RateLimited("Mailgun rate limited: " + body, retryAfter));
            }
            log.error("[MAILGUN_SEND_EXCEPTION] status={} error={}", e.getStatusCode(), e.getMessage(), e);
            return Either.left(new EmailError.EmailProviderError("Mailgun exception: " + e.getMessage()));
        } catch (Exception e) {
            log.error("[MAILGUN_SEND_EXCEPTION] error={}", e.getMessage(), e);
            return Either.left(new EmailError.EmailProviderError("Mailgun exception: " + e.getMessage()));
        }
    }

    /**
     * Pulls the reset instant out of a 420 body, whose message ends with
     * {@code try again after Mon, 24 Aug 2026 21:00:19 UTC}. Returns null when the phrase is
     * absent or unparseable — the wording is Mailgun's prose, not a documented contract, so the
     * caller must treat a missing time as "unknown" and fall back to its own backoff.
     */
    private static Instant parseRetryAfter(String body) {
        if (body == null) {
            return null;
        }
        Matcher matcher = RETRY_AFTER_PATTERN.matcher(body);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(matcher.group(1).replace("UTC", "GMT")));
        } catch (DateTimeParseException e) {
            log.warn("[MAILGUN_RETRY_AFTER_UNPARSEABLE] value={}", matcher.group(1));
            return null;
        }
    }

    @Override
    public Either<EmailError, Void> sendSingle(
            String recipientEmail, EmailContent content, EmailCommand.Priority priority) {
        return sendBatch(List.of(recipientEmail), content, priority);
    }
}
