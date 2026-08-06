package com.ligitabl.api.notification.email;

import java.util.List;

import com.ligitabl.api.shared.Either;

/**
 * Sends rendered email.
 *
 * <p>Both methods take the whole {@link EmailContent} rather than loose subject/body strings.
 */
public interface EmailProvider {
    Either<EmailError, Void> sendBatch(
            List<String> recipientEmails, EmailContent content, EmailCommand.Priority priority);

    Either<EmailError, Void> sendSingle(String recipientEmail, EmailContent content, EmailCommand.Priority priority);
}
