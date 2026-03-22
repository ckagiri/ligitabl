package com.ligitabl.api.notification;

import java.util.List;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ligitabl.api.shared.Either;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnProperty(name = "ligitabl.email.provider", havingValue = "gmail-smtp")
@Slf4j
public class GmailSmtpEmailProvider implements EmailProvider {

    private final Session session;
    private final String fromEmail;
    private final String fromName;

    public GmailSmtpEmailProvider(
            @Value("${ligitabl.email.gmail-smtp.username}") String username,
            @Value("${ligitabl.email.gmail-smtp.password}") String password,
            @Value("${ligitabl.email.from-email}") String fromEmail,
            @Value("${ligitabl.email.from-name:LigiTabl}") String fromName) {
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.session = createSession(username, password);
    }

    private Session createSession(String username, String password) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");

        return Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }

    @Override
    public Either<EmailError, Void> sendBatch(
            List<String> recipientEmails, String subject, String htmlBody, EmailCommand.Priority priority) {
        if (recipientEmails == null || recipientEmails.isEmpty()) {
            return Either.left(new EmailError.NoValidRecipients());
        }

        EmailError firstError = null;
        for (String recipient : recipientEmails) {
            var result = sendSingle(recipient, subject, htmlBody, priority);
            if (result.isLeft()) {
                if (firstError == null) {
                    firstError = result.getLeft();
                }
                continue;
            }
        }

        if (firstError != null) {
            return Either.left(firstError);
        }

        log.info("[GMAIL_SMTP_SUCCESS] Sent {} emails", recipientEmails.size());
        return Either.right(null);
    }

    @Override
    public Either<EmailError, Void> sendSingle(
            String recipientEmail, String subject, String htmlBody, EmailCommand.Priority priority) {
        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail, fromName));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
            message.setSubject(subject);
            message.setContent(htmlBody, "text/html; charset=utf-8");

            Transport.send(message);
            log.info("[GMAIL_SMTP_SENT] to={} priority={}", recipientEmail, priority);
            return Either.right(null);
        } catch (Exception e) {
            log.error("[GMAIL_SMTP_SEND_ERROR] recipient={}", recipientEmail, e);
            return Either.left(new EmailError.EmailProviderError("SMTP send failed: " + e.getMessage()));
        }
    }
}
