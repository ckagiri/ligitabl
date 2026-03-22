package com.ligitabl.api.notification;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ligitabl.api.shared.Either;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.ConfigurationSetDoesNotExistException;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.MailFromDomainNotVerifiedException;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.MessageRejectedException;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SesException;

@Component
@ConditionalOnProperty(name = "ligitabl.email.provider", havingValue = "aws-ses")
@Slf4j
public class AwsSesEmailProvider implements EmailProvider {

    private static final int MAX_RECIPIENTS_PER_BATCH = 50;

    private final SesClient sesClient;
    private final String fromEmail;
    private final String fromName;

    public AwsSesEmailProvider(
            SesClient sesClient,
            @Value("${ligitabl.email.from-email}") String fromEmail,
            @Value("${ligitabl.email.from-name:LigiPredictor}") String fromName) {
        this.sesClient = sesClient;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
    }

    @Override
    public Either<EmailError, Void> sendBatch(
            List<String> recipientEmails, String subject, String htmlBody, EmailCommand.Priority priority) {
        if (recipientEmails == null || recipientEmails.isEmpty()) {
            return Either.left(new EmailError.NoValidRecipients());
        }

        List<List<String>> batches = partition(recipientEmails, MAX_RECIPIENTS_PER_BATCH);
        log.info("[AWS_SES_SEND] Sending to {} recipients in {} batches", recipientEmails.size(), batches.size());

        for (int index = 0; index < batches.size(); index++) {
            List<String> batch = batches.get(index);
            var result = sendToRecipients(batch, subject, htmlBody, priority);
            if (result.isLeft()) {
                log.error("[AWS_SES_BATCH_FAILED] batch={}/{} recipients={}", index + 1, batches.size(), batch.size());
                continue;
            }

            log.info("[AWS_SES_BATCH_SENT] batch={}/{} recipients={}", index + 1, batches.size(), batch.size());
        }

        log.info("[AWS_SES_SUCCESS] Attempted delivery to {} recipients", recipientEmails.size());
        return Either.right(null);
    }

    @Override
    public Either<EmailError, Void> sendSingle(
            String recipientEmail, String subject, String htmlBody, EmailCommand.Priority priority) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return Either.left(new EmailError.NoValidRecipients());
        }

        return sendToRecipients(List.of(recipientEmail), subject, htmlBody, priority);
    }

    private Either<EmailError, Void> sendToRecipients(
            List<String> recipientEmails, String subject, String htmlBody, EmailCommand.Priority priority) {
        try {
            SendEmailResponse response = sesClient.sendEmail(SendEmailRequest.builder()
                    .source(formatFromAddress())
                    .destination(Destination.builder().toAddresses(recipientEmails).build())
                    .message(Message.builder()
                            .subject(Content.builder().charset("UTF-8").data(subject).build())
                            .body(Body.builder()
                                    .html(Content.builder().charset("UTF-8").data(htmlBody).build())
                                    .build())
                            .build())
                    .build());
            log.info("[AWS_SES_SENT] messageId={} recipients={} priority={}", response.messageId(), recipientEmails.size(), priority);
            return Either.right(null);
        } catch (MessageRejectedException e) {
            log.error("[AWS_SES_MESSAGE_REJECTED] recipients={}", recipientEmails, e);
            return Either.left(new EmailError.EmailProviderError("SES rejected message: " + e.getMessage()));
        } catch (MailFromDomainNotVerifiedException e) {
            log.error("[AWS_SES_DOMAIN_NOT_VERIFIED] fromEmail={}", fromEmail, e);
            return Either.left(new EmailError.EmailProviderError("SES sender not verified: " + fromEmail));
        } catch (ConfigurationSetDoesNotExistException e) {
            log.error("[AWS_SES_CONFIGURATION_SET_MISSING] configurationSet={}", configurationSetName, e);
            return Either.left(new EmailError.EmailProviderError(
                    "SES configuration set not found: " + configurationSetName));
        } catch (SesException e) {
            String providerMessage = e.awsErrorDetails() == null
                    ? e.getMessage()
                    : e.awsErrorDetails().errorMessage();
            log.error("[AWS_SES_ERROR] recipients={}", recipientEmails, e);
            return Either.left(new EmailError.EmailProviderError("SES error: " + providerMessage));
        } catch (Exception e) {
            log.error("[AWS_SES_SEND_ERROR] recipients={}", recipientEmails, e);
            return Either.left(new EmailError.EmailProviderError("SES send failed: " + e.getMessage()));
        }
    }

    private List<List<String>> partition(List<String> recipients, int batchSize) {
        java.util.ArrayList<List<String>> partitions = new java.util.ArrayList<>();
        for (int index = 0; index < recipients.size(); index += batchSize) {
            partitions.add(recipients.subList(index, Math.min(index + batchSize, recipients.size())));
        }
        return partitions;
    }

    private String formatFromAddress() {
        if (fromName == null || fromName.isBlank()) {
            return fromEmail;
        }

        return String.format("%s <%s>", fromName, fromEmail);
    }
}
