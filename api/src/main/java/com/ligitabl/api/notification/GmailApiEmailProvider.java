package com.ligitabl.api.notification;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.List;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.ligitabl.api.shared.Either;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnProperty(name = "ligitabl.email.provider", havingValue = "gmail-api")
@Slf4j
public class GmailApiEmailProvider implements EmailProvider {
    private static final String GMAIL_SEND_SCOPE = "https://www.googleapis.com/auth/gmail.send";
    private static final int BATCH_SIZE = 100; // Gmail API recommended limit


    private final Gmail gmailService;
    private final String fromEmail;
    private final String fromName;
    private final String userId;

    public GmailApiEmailProvider(
            @Value("${ligitabl.email.gmail-api.credentials-path}") String credentialsPath,
            @Value("${ligitabl.email.gmail-api.user-id:me}") String userId,
            @Value("${ligitabl.email.from-email:noreply@ligitabl.local}") String fromEmail,
            @Value("${ligitabl.email.from-name:LigiTabl}") String fromName)
            throws Exception {
        this.fromEmail = fromEmail;
        this.fromName = fromName;
        this.userId = userId;
        this.gmailService = createGmailService(credentialsPath, userId);
    }

    @Override
    public Either<EmailError, Void> sendBatch(
            List<String> recipientEmails, String subject, String htmlBody, EmailCommand.Priority priority) {
        if (recipientEmails == null || recipientEmails.isEmpty()) {
            return Either.left(new EmailError.NoValidRecipients());
        }

        log.info("[GMAIL_SEND] Sending to {} recipients", recipientEmails.size());

        for (String recipient : recipientEmails) {
            var result = sendSingle(recipient, subject, htmlBody, priority);
            if (result.isLeft()) {
                log.error("[GMAIL_FAILED] Failed to send to {}", recipient);

                return result;
            }
        }

        log.info("[GMAIL_SUCCESS] Sent {} emails", recipientEmails.size());

        return Either.right(null);
    }

    @Override
    public Either<EmailError, Void> sendSingle(
            String recipientEmail, String subject, String htmlBody, EmailCommand.Priority priority) {
        try {
            MimeMessage mimeMessage = createMimeMessage(recipientEmail, subject, htmlBody);
            Message message = createGmailMessage(mimeMessage);

            gmailService.users().messages().send(userId, message).execute();
            log.debug("[GMAIL_SENT] To: {}", recipientEmail);

            return Either.right(null);
        } catch (Exception e) {
            log.error("[GMAIL_EMAIL_FAILED] recipient={} subject={}", recipientEmail, subject, e);
            return Either.left(new EmailError.EmailProviderError(e.getMessage()));
        }
    }

    private Gmail createGmailService(String credentialsPath, String userId)
            throws Exception {
        try (FileInputStream inputStream = new FileInputStream(credentialsPath)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(inputStream).createScoped(List.of(GMAIL_SEND_SCOPE));

            return new Gmail.Builder(
                            GoogleNetHttpTransport.newTrustedTransport(),
                            GsonFactory.getDefaultInstance(),
                            new HttpCredentialsAdapter(credentials))
                    .setApplicationName("LigiTabl")
                    .build();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to initialize Gmail transport", e);
        }
    }

    private MimeMessage createMimeMessage(String recipientEmail, String subject, String htmlBody) throws Exception {
        Session session = Session.getDefaultInstance(new Properties(), null);
        MimeMessage mimeMessage = new MimeMessage(session);
        mimeMessage.setFrom(new InternetAddress(fromEmail, fromName, StandardCharsets.UTF_8.name()));
        mimeMessage.addRecipient(jakarta.mail.Message.RecipientType.TO, new InternetAddress(recipientEmail));
        mimeMessage.setSubject(subject, StandardCharsets.UTF_8.name());
        mimeMessage.setContent(htmlBody, "text/html; charset=utf-8");
        return mimeMessage;
    }

    private Message createGmailMessage(MimeMessage mimeMessage) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            mimeMessage.writeTo(outputStream);
            String encodedEmail =
                    Base64.getUrlEncoder().withoutPadding().encodeToString(outputStream.toByteArray());
            Message message = new Message();
            message.setRaw(encodedEmail);
            return message;
        }
    }
}
