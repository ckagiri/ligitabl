package com.ligitabl.api.web.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.shared.Either;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.domain.PasswordResetToken;
import com.ligitabl.model.repo.PasswordResetTokenRepo;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RequestPasswordResetUseCase {
    private final UserRepo userRepo;
    private final PasswordResetTokenRepo tokenRepo;
    private final PasswordResetEmailService emailService;

    @Value("${ligitabl.frontend.url:http://localhost:8080}")
    private String frontendUrl;

    @Value("${ligitabl.security.password-reset-token-validity-minutes:30}")
    private int tokenValidityMinutes;

    @Transactional
    public Either<PasswordResetError, PasswordResetResult> execute(String emailStr) {
        try {
            Email email = Email.create(emailStr);
            var userResult = userRepo.findByEmail(email);

            // SECURITY: Don't reveal if email exists (prevents enumeration attacks)
            // Always return success
            if (userResult.isEmpty()) {
                log.info("[PASSWORD_RESET] Email not found (returning success): {}", email);
                return Either.right(new PasswordResetResult.EmailSent(email.value()));
            }

            var user = userResult.get();
            tokenRepo.invalidateAllForUser(user.getId());

            PasswordResetToken token = PasswordResetToken.create(user.getId(), tokenValidityMinutes);
            tokenRepo.save(token);

            log.info("[PASSWORD_RESET] Token created userId={} expiresAt={}",
                user.getId(), token.getExpiresAt());

            String resetUrl = frontendUrl + "/auth/reset-password?token=" + token.getToken();
            emailService.sendPasswordResetEmail(email.value(), resetUrl, tokenValidityMinutes);

            return Either.right(new PasswordResetResult.EmailSent(email.value()));
        } catch (Exception e) {
            log.error("[PASSWORD_RESET] Email send failed email={}", emailStr);
            return Either.left(new PasswordResetError.UnexpectedError());
        }
    }

    public sealed interface PasswordResetError {
        record UnexpectedError() implements PasswordResetError {}
    }

    public sealed interface PasswordResetResult {
        record EmailSent(String email) implements PasswordResetResult {}
    }
}
