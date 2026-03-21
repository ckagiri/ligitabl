package com.ligitabl.api.notification;

import java.util.List;

import com.ligitabl.api.shared.Either;

public interface EmailProvider {
        Either<EmailError, Void> sendBatch(
            List<String> recipientEmails, String subject, String htmlBody, EmailCommand.Priority priority);

        Either<EmailError, Void> sendSingle(
            String recipientEmail, String subject, String htmlBody, EmailCommand.Priority priority);
}
