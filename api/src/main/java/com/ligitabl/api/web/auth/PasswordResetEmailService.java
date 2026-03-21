package com.ligitabl.api.web.auth;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.ligitabl.api.notification.EmailCommand;
import com.ligitabl.api.notification.EmailError;
import com.ligitabl.api.notification.EmailProvider;
import com.ligitabl.api.notification.EmailTemplateRenderer;
import com.ligitabl.api.shared.Either;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetEmailService {
    private final EmailProvider emailProvider;
    private final EmailTemplateRenderer templateRenderer;

    public Either<EmailError, Void> sendPasswordResetEmail(
            String recipientEmail, String resetUrl, int expiryMinutes) {
        Map<String, Object> templateData = Map.of(
                "resetUrl", resetUrl,
                "expiryMinutes", expiryMinutes,
                "recipientEmail", recipientEmail);

        var renderedResult = templateRenderer.render(EmailCommand.EmailType.PASSWORD_RESET, templateData);
        if (renderedResult.isLeft()) {
            log.error("[PASSWORD_RESET_EMAIL_RENDER_FAILED] email={}", recipientEmail);
            return Either.left(renderedResult.getLeft());
        }

        var rendered = renderedResult.get();

        log.info("[PASSWORD_RESET_EMAIL] Sending to {}", recipientEmail);
        return emailProvider.sendSingle(
                recipientEmail,
                rendered.subject(),
                rendered.htmlBody(),
                EmailCommand.Priority.HIGH);
    }

    public Either<EmailError, Void> sendPasswordResetConfirmation(String recipientEmail) {
        Map<String, Object> templateData = Map.of("recipientEmail", recipientEmail);

        var contentResult = templateRenderer.render(EmailCommand.EmailType.PASSWORD_RESET_CONFIRMATION, templateData);
        if (contentResult.isLeft()) {
            return Either.left(contentResult.getLeft());
        }

        var content = contentResult.get();

        log.info("[PASSWORD_RESET_EMAIL] Sending to {}", recipientEmail);
        return emailProvider.sendSingle(
                recipientEmail,
                content.subject(),
                content.htmlBody(),
                EmailCommand.Priority.NORMAL);
    }
}
