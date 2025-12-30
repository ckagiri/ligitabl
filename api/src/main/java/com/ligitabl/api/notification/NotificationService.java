package com.ligitabl.api.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Email Service (Stub Implementation)
 *
 * Currently logs admin alerts to console.
 *
 * MIGRATION PATH TO REAL SERVICE:
 * ================================
 *
 * Option 1: Spring Mail (Simple)
 * -------------------------------
 * 1. Add dependency: spring-boot-starter-mail
 * 2. Configure SMTP in application.yml:
 *    spring:
 *      mail:
 *        host: smtp.gmail.com
 *        port: 587
 *        username: your-email@gmail.com
 *        password: app-password
 *        properties:
 *          mail.smtp.auth: true
 *          mail.smtp.starttls.enable: true
 *
 * 3. Replace implementation:
 *    @Autowired private JavaMailSender mailSender;
 *
 *    public void sendAdminAlert(String subject, String body) {
 *        SimpleMailMessage message = new SimpleMailMessage();
 *        message.setTo(adminEmail);
 *        message.setSubject(subject);
 *        message.setText(body);
 *        mailSender.send(message);
 *    }
 *
 * Option 2: SendGrid (Recommended for Production)
 * ------------------------------------------------
 * 1. Add dependency: com.sendgrid:sendgrid-java:4.9.3
 * 2. Add API key to application.yml:
 *    sendgrid:
 *      api-key: ${SENDGRID_API_KEY}
 *
 * 3. Replace implementation:
 *    @Autowired private SendGrid sendGrid;
 *
 *    public void sendAdminAlert(String subject, String body) {
 *        Email from = new Email("noreply@ligitabl.com");
 *        Email to = new Email(adminEmail);
 *        Content content = new Content("text/plain", body);
 *        Mail mail = new Mail(from, subject, to, content);
 *
 *        Request request = new Request();
 *        request.setMethod(Method.POST);
 *        request.setEndpoint("mail/send");
 *        request.setBody(mail.build());
 *        sendGrid.api(request);
 *    }
 *
 * Option 3: AWS SES (Best for AWS deployments)
 * ---------------------------------------------
 * 1. Add dependency: com.amazonaws:aws-java-sdk-ses:1.12.x
 * 2. Configure AWS credentials
 * 3. Replace implementation:
 *    @Autowired private AmazonSimpleEmailService sesClient;
 *
 *    public void sendAdminAlert(String subject, String body) {
 *        SendEmailRequest request = new SendEmailRequest()
 *            .withDestination(new Destination()
 *                .withToAddresses(adminEmail))
 *            .withMessage(new Message()
 *                .withSubject(new Content(subject))
 *                .withBody(new Body()
 *                    .withText(new Content(body))))
 *            .withSource("noreply@ligitabl.com");
 *
 *        sesClient.sendEmail(request);
 *    }
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

/**
 * Admin Notification Service
 *
 * Facade for sending admin notifications.
 * Formats messages appropriately.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminNotificationService {

    private final EmailService emailService;

    /**
     * Notify admin that round finalization is blocked
     */
    public void notifyBlockedFinalization(
            UUID roundId,
            int roundPosition,
            List<UUID> blockingMatchIds,
            List<String> matchDetails) {

        var subject = String.format(
                "ACTION REQUIRED: Round %d Finalization Blocked",
                roundPosition
        );

        var body = new StringBuilder();
        body.append("Round finalization cannot proceed due to matches requiring admin resolution.\n\n");
        body.append(String.format("Round ID: %s\n", roundId));
        body.append(String.format("Round Position: %d\n", roundPosition));
        body.append(String.format("Blocking Matches: %d\n\n", blockingMatchIds.size()));
        body.append("Match Details:\n");

        for (String detail : matchDetails) {
            body.append(detail).append("\n");
        }

        body.append("\nRequired Actions:\n");
        body.append("1. Review each match in CANCELLED/SUSPENDED status\n");
        body.append("2. For CANCELLED matches, choose:\n");
        body.append("   - FINISHED (award forfeit result 3-0)\n");
        body.append("   - POSTPONED (reschedule to future round)\n");
        body.append("   - SCHEDULED (replay in same round - rare)\n");
        body.append("3. For SUSPENDED matches:\n");
        body.append("   - Move to CANCELLED (then follow above)\n");
        body.append("\nRound will finalize automatically once all matches are resolved.\n");

        log.warn("Sending blocked finalization notification to admin");
        emailService.sendAdminAlert(subject, body.toString());
    }

    /**
     * Notify admin of repeated sync failures
     */
    public void notifySyncFailure(String competitionCode, String error, int failureCount) {
        var subject = String.format(
                "ALERT: Match Sync Failure - %s (%d consecutive failures)",
                competitionCode,
                failureCount
        );

        var body = new StringBuilder();
        body.append("Match synchronization has failed multiple times.\n\n");
        body.append(String.format("Competition: %s\n", competitionCode));
        body.append(String.format("Consecutive Failures: %d\n", failureCount));
        body.append(String.format("Error: %s\n\n", error));
        body.append("Possible causes:\n");
        body.append("1. Football Data API is down\n");
        body.append("2. Rate limit exceeded\n");
        body.append("3. Invalid API token\n");
        body.append("4. Network connectivity issues\n\n");
        body.append("Please check the application logs for more details.\n");

        log.warn("Sending sync failure notification to admin");
        emailService.sendAdminAlert(subject, body.toString());
    }

    /**
     * Notify admin of season completion
     */
    public void notifySeasonCompleted(UUID seasonId, String seasonName, int finalMatchday) {
        var subject = String.format("Season Completed: %s", seasonName);

        var body = new StringBuilder();
        body.append("A season has been completed.\n\n");
        body.append(String.format("Season ID: %s\n", seasonId));
        body.append(String.format("Season Name: %s\n", seasonName));
        body.append(String.format("Final Matchday: %d\n\n", finalMatchday));
        body.append("All rounds have been finalized and results calculated.\n");
        body.append("Final leaderboards are now available.\n");

        log.info("Sending season completion notification to admin");
        emailService.sendAdminAlert(subject, body.toString());
    }

    /**
     * Notify admin that circuit breaker opened
     */
    public void notifyCircuitBreakerOpened(int failures, long recoveryHours) {
        var subject = String.format("CRITICAL: Match Sync Circuit Breaker Opened");

        var body = new StringBuilder();
        body.append("The match synchronization circuit breaker has opened due to repeated failures.\n\n");
        body.append(String.format("Consecutive Failures: %d\n", failures));
        body.append(String.format("Recovery Wait Time: %d hour(s)\n\n", recoveryHours));
        body.append("The system will automatically retry after the wait period.\n");
        body.append("If this issue persists, please check:\n");
        body.append("1. Football Data API status\n");
        body.append("2. API token validity\n");
        body.append("3. Network connectivity\n");
        body.append("4. Database connectivity\n");
        body.append("5. Application logs for detailed errors\n");

        log.error("Sending circuit breaker opened notification to admin");
        emailService.sendAdminAlert(subject, body.toString());
    }

    /**
     * Notify admin that circuit breaker recovered
     */
    public void notifyCircuitBreakerRecovered(int previousFailures) {
        var subject = "RESOLVED: Match Sync Circuit Breaker Recovered";

        var body = new StringBuilder();
        body.append("The match synchronization circuit breaker has recovered.\n\n");
        body.append(String.format("Previous Failures: %d\n", previousFailures));
        body.append("System has returned to normal operation.\n");

        log.info("Sending circuit breaker recovered notification to admin");
        emailService.sendAdminAlert(subject, body.toString());
    }
}
