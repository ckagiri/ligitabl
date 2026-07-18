package com.ligitabl.api.notification.email;

import java.net.URI;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.ligitabl.api.shared.Either;

import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnProperty(name = "ligitabl.email.provider", havingValue = "mailgun")
@Slf4j
public class MailgunEmailProvider implements EmailProvider {

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
            List<String> recipientEmails, String subject, String htmlBody, EmailCommand.Priority priority) {

        log.info("[MAILGUN_SEND_BATCH] recipients={} subject={}", recipientEmails.size(), subject);

        try {
            URI uri = URI.create(String.format("%s/%s/messages", apiBaseUrl, domain));

            String encodedAuth = Base64.getEncoder().encodeToString(("api:" + apiKey).getBytes());

            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("from", String.format("%s <%s>", fromName, fromEmail));
            formData.add("subject", subject);
            formData.add("html", htmlBody);

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

        } catch (Exception e) {
            log.error("[MAILGUN_SEND_EXCEPTION] error={}", e.getMessage(), e);
            return Either.left(new EmailError.EmailProviderError("Mailgun exception: " + e.getMessage()));
        }
    }

    @Override
    public Either<EmailError, Void> sendSingle(
            String recipientEmail, String subject, String htmlBody, EmailCommand.Priority priority) {
        return sendBatch(List.of(recipientEmail), subject, htmlBody, priority);
    }
}
