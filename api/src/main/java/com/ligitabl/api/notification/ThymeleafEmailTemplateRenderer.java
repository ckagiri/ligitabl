package com.ligitabl.api.notification;

import java.util.Map;

import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import com.ligitabl.api.shared.Either;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ThymeleafEmailTemplateRenderer implements EmailTemplateRenderer {
    private final TemplateEngine templateEngine;

    @Override
    public Either<EmailError, EmailContent> render(EmailCommand.EmailType emailType, Map<String, Object> templateData) {
        try {
            Context context = new Context();
            context.setVariables(templateData);

            String templateName = templateNameFor(emailType);
            String subject = subjectFor(emailType);
            String htmlBody = templateEngine.process(templateName, context);
            String textBody = htmlBody.replaceAll("<[^>]*>", "").trim();

            return Either.right(new EmailContent(subject, htmlBody, textBody));
        } catch (Exception e) {
            return Either.left(new EmailError.TemplateRenderError(emailType.name(), e.getMessage()));
        }
    }

    private String templateNameFor(EmailCommand.EmailType emailType) {
        return switch (emailType) {
            case PASSWORD_RESET -> "email/password-reset";
            case PASSWORD_RESET_CONFIRMATION -> "email/password-reset-confirmation";
        };
    }

    private String subjectFor(EmailCommand.EmailType emailType) {
        return switch (emailType) {
            case PASSWORD_RESET -> "Reset your LigiTabl password";
            case PASSWORD_RESET_CONFIRMATION -> "Your LigiTabl password has been changed";
        };
    }
}
