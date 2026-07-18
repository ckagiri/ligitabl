package com.ligitabl.api.notification.email;

import java.util.Map;

import com.ligitabl.api.shared.Either;

public interface EmailTemplateRenderer {
    Either<EmailError, EmailContent> render(EmailCommand.EmailType emailType, Map<String, Object> templateData);
}
