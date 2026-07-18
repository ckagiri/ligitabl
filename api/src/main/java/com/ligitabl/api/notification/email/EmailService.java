package com.ligitabl.api.notification.email;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Email Service (Stub Implementation)
 * Currently logs admin alerts to console.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    // TODO: Move to application.yml
    private static final String ADMIN_EMAIL = "admin@ligitabl.com";

    /**
     * Send email to admin
     *
     * @param subject Email subject
     * @param body Email body (plain text for now)
     */
    public void sendAdminAlert(String subject, String body) {
        log.warn("========================================");
        log.warn("ADMIN ALERT EMAIL");
        log.warn("========================================");
        log.warn("To: {}", ADMIN_EMAIL);
        log.warn("Subject: {}", subject);
        log.warn("Body:");
        log.warn(body);
        log.warn("========================================");

        // TODO: Implement actual email sending
        // See class-level documentation for migration options
    }

    /**
     * Send email with HTML content (for future use)
     */
    public void sendAdminAlertHtml(String subject, String htmlBody) {
        // For now, strip HTML and send as plain text
        String plainText = htmlBody.replaceAll("<[^>]*>", "");
        sendAdminAlert(subject, plainText);

        // TODO: Implement HTML email sending
    }
}
