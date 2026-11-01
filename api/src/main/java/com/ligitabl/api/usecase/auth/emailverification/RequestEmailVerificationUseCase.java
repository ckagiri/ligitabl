package com.ligitabl.api.usecase.auth.emailverification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.identity.EmailVerificationToken;
import com.ligitabl.model.repo.EmailVerificationTokenRepo;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RequestEmailVerificationUseCase {
    private final UserRepo userRepo;
    private final EmailVerificationTokenRepo tokenRepo;
    private final EmailVerificationEmailService emailService;
    private final Clock clock;

    @Value("${ligitabl.frontend.url:http://localhost:8080}")
    private String frontendUrl;

    @Value("${ligitabl.security.email-verification-token-validity-hours:48}")
    private int tokenValidityHours;

    @Value("${ligitabl.security.email-verification-resend-cooldown-minutes:2}")
    private int resendCooldownMinutes;

    @Transactional
    public Either<RequestEmailVerificationError, RequestEmailVerificationResult> execute(UUID userId) {
        try {
            var userResult = userRepo.findById(userId);
            if (userResult.isEmpty()) {
                log.error("[EMAIL_VERIFICATION] User not found userId={}", userId);
                return Either.left(new RequestEmailVerificationError.UserNotFound());
            }

            var user = userResult.get();
            if (user.isEmailVerified()) {
                return Either.left(new RequestEmailVerificationError.AlreadyVerified());
            }

            // One read for the throttle test and the new token, so the next resend's window is
            // measured from the instant this call checked.
            Instant now = clock.instant();

            var latest = tokenRepo.findLatestForUser(userId);
            if (latest.isPresent()) {
                Instant earliestResend = latest.get().getCreatedAt().plus(Duration.ofMinutes(resendCooldownMinutes));
                if (now.isBefore(earliestResend)) {
                    log.info("[EMAIL_VERIFICATION] Resend throttled userId={}", userId);
                    return Either.left(new RequestEmailVerificationError.ResendTooSoon());
                }
            }

            tokenRepo.invalidateAllForUser(userId);

            EmailVerificationToken token = EmailVerificationToken.create(userId, tokenValidityHours, now);
            tokenRepo.save(token);

            log.info("[EMAIL_VERIFICATION] Token created userId={} expiresAt={}", userId, token.getExpiresAt());

            String verificationUrl = frontendUrl + "/auth/verify-email?token=" + token.getToken();
            var sendResult =
                    emailService.sendVerificationEmail(user.getEmail().value(), verificationUrl, tokenValidityHours);
            if (sendResult.isLeft()) {
                // Non-fatal, matching the password-reset flow: the token stays valid and the
                // user can retry from the profile page after the cooldown.
                log.error(
                        "[EMAIL_VERIFICATION] Email delivery failed userId={} error={}", userId, sendResult.getLeft());
            }

            return Either.right(
                    new RequestEmailVerificationResult.EmailSent(user.getEmail().value()));
        } catch (Exception e) {
            log.error("[EMAIL_VERIFICATION] Unexpected failure userId={}", userId, e);
            return Either.left(new RequestEmailVerificationError.UnexpectedError());
        }
    }
}
