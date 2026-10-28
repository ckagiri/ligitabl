package com.ligitabl.api.usecase.auth.emailverification;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.usecase.shared.email.EmailCommand;
import com.ligitabl.api.usecase.shared.email.EmailError;
import com.ligitabl.api.usecase.shared.email.EmailProvider;
import com.ligitabl.api.usecase.shared.email.EmailTemplateRenderer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationEmailService {
    private final EmailProvider emailProvider;
    private final EmailTemplateRenderer templateRenderer;

    public Either<EmailError, Void> sendVerificationEmail(
            String recipientEmail, String verificationUrl, int expiryHours) {
        Map<String, Object> templateData = Map.of(
                "verificationUrl", verificationUrl,
                "expiryHours", expiryHours,
                "recipientEmail", recipientEmail);

        var renderedResult = templateRenderer.render(EmailCommand.EmailType.EMAIL_VERIFICATION, templateData);
        if (renderedResult.isLeft()) {
            log.error("[EMAIL_VERIFICATION_RENDER_FAILED] email={}", recipientEmail);
            return Either.left(renderedResult.getLeft());
        }

        var rendered = renderedResult.get();

        log.info("[EMAIL_VERIFICATION_EMAIL] Sending to {}", recipientEmail);
        return emailProvider.sendSingle(recipientEmail, rendered, EmailCommand.Priority.HIGH);
    }
}
