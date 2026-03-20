package com.ligitabl.api.web.auth;

import org.springframework.stereotype.Service;

import com.ligitabl.api.notification.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetEmailService {
    private final EmailService emailService;

    public void sendPasswordResetEmail(String recipientEmail, String resetUrl, int expiryMinutes) {
        String subject = "Reset your LigiTabl password";
        String body = """
                Password reset requested

                Recipient: %s
                Reset URL: %s
                Expires in: %d minutes
                """
                .formatted(recipientEmail, resetUrl, expiryMinutes);

        log.info("[PASSWORD_RESET_EMAIL] Sending to {}", recipientEmail);

        emailService.sendAdminAlert(subject, body);
    }

    public void sendPasswordResetConfirmation(String recipientEmail) {
        String subject = "Your LigiTabl password has been changed";
        String body = """
                Password reset completed

                Recipient: %s
                """
                .formatted(recipientEmail);

        log.info("[PASSWORD_RESET_EMAIL] Sending to {}", recipientEmail);
        emailService.sendAdminAlert(subject, body);
    }
}
