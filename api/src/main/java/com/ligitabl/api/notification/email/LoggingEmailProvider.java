package com.ligitabl.api.notification.email;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ligitabl.api.shared.Either;

import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnProperty(name = "ligitabl.email.provider", havingValue = "logging", matchIfMissing = true)
@Slf4j
public class LoggingEmailProvider implements EmailProvider {

    @Override
    public Either<EmailError, Void> sendBatch(
            List<String> recipientEmails, EmailContent content, EmailCommand.Priority priority) {
        if (recipientEmails == null || recipientEmails.isEmpty()) {
            return Either.left(new EmailError.NoValidRecipients());
        }

        log.info("[LOGGING_EMAIL_SEND] Sending to {} recipients", recipientEmails.size());

        for (String recipient : recipientEmails) {
            var result = sendSingle(recipient, content, priority);
            if (result.isLeft()) {
                log.error("[LOGGING_EMAIL_FAILED] Failed to send to {}", recipient);
                continue;
            }
        }

        log.info("[LOGGING_EMAIL_SUCCESS] Sent {} emails", recipientEmails.size());
        return Either.right(null);
    }

    @Override
    public Either<EmailError, Void> sendSingle(
            String recipientEmail, EmailContent content, EmailCommand.Priority priority) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return Either.left(new EmailError.NoValidRecipients());
        }

        log.warn("========================================");
        log.warn("EMAIL (LOGGING PROVIDER)");
        log.warn("========================================");
        log.warn("To: {}", recipientEmail);
        log.warn("Subject: {}", content.subject());
        log.warn("Priority: {}", priority);
        // The text part is what local verification should read: it is short, and it is the half
        // that used to be silently discarded.
        log.warn("Body (text):");
        log.warn(content.textBody());
        log.warn("Body (HTML):");
        log.warn(content.htmlBody());
        log.warn("========================================");

        return Either.right(null);
    }
}
