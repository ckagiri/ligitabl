package com.ligitabl.api.notification.email;

import java.util.List;

import com.ligitabl.api.shared.Either;

/**
 * Sends rendered email.
 *
 * <p>Both methods take the whole {@link EmailContent} rather than loose subject/body strings. That
 * is deliberate: the alternative was a fourth and fifth adjacent {@code String} parameter, which a
 * caller can transpose without the compiler noticing. It also means the text alternative cannot be
 * dropped at a call site — every caller already holds an {@code EmailContent} from the renderer,
 * and previously threw its {@code textBody} away by passing the two fields it wanted.
 */
public interface EmailProvider {
    Either<EmailError, Void> sendBatch(
            List<String> recipientEmails, EmailContent content, EmailCommand.Priority priority);

    Either<EmailError, Void> sendSingle(String recipientEmail, EmailContent content, EmailCommand.Priority priority);
}
