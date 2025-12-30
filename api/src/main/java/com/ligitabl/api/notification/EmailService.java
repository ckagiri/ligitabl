package com.ligitabl.api.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
