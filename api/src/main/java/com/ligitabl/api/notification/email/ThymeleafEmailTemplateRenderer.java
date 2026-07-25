package com.ligitabl.api.notification.email;

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
            String subject = subjectFor(emailType, templateData);
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
            case EMAIL_VERIFICATION -> "email/email-verification";
            case ROUND_RESULTS -> "email/round-results";
        };
    }

    private String subjectFor(EmailCommand.EmailType emailType, Map<String, Object> templateData) {
        return switch (emailType) {
            case PASSWORD_RESET -> "Reset your LigiPredictor password";
            case PASSWORD_RESET_CONFIRMATION -> "Your LigiPredictor password has been changed";
            case EMAIL_VERIFICATION -> "Verify your LigiPredictor email";
            case ROUND_RESULTS -> "Your Gameweek %s Results — %s points!"
                    .formatted(templateData.get("round"), templateData.get("score"));
        };
    }
}
