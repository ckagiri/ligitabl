package com.ligitabl.api.web.auth;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.shared.Either;
import com.ligitabl.model.auth.Password;
import com.ligitabl.model.domain.service.PasswordHasher;
import com.ligitabl.model.repo.PasswordResetTokenRepo;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResetPasswordUseCase {
    private final UserRepo userRepo;
    private final PasswordResetTokenRepo tokenRepo;
    private final PasswordHasher passwordHasher;
    private final PasswordResetEmailService emailService;

    @Transactional
    public Either<ResetError, ResetResult> execute(String token, String newPassword) {
        if (token == null || token.isBlank()) {
            return Either.left(new ResetError.InvalidToken());
        }

        Password.Plaintext plaintext;
        try {
            plaintext = Password.Plaintext.create(newPassword);
        } catch (IllegalArgumentException e) {
            return Either.left(new ResetError.WeakPassword(e.getMessage()));
        }

        var tokenResult = tokenRepo.findByToken(token);
        if (tokenResult.isEmpty()) {
            log.warn("[PASSWORD_RESET] Token not found: {}", token);
            return Either.left(new ResetError.InvalidToken());
        }

        var resetToken = tokenResult.get();
        if (!resetToken.isValid()) {
            log.warn("[PASSWORD_RESET] Token invalid: used={} expired={}", resetToken.isUsed(), resetToken.isExpired());

            if (resetToken.isUsed()) {
                return Either.left(new ResetError.TokenAlreadyUsed());
            } else {
                return Either.left(new ResetError.TokenExpired());
            }
        }

        var userResult = userRepo.findById(resetToken.getUserId());
        if (userResult.isEmpty()) {
            log.error("[PASSWORD_RESET] User not found userId={}", resetToken.getUserId());
            return Either.left(new ResetError.InvalidToken());
        }

        var user = userResult.get();
        var hashedPassword = passwordHasher.hash(plaintext);
        userRepo.updatePassword(user.getId(), hashedPassword);

        var usedToken = resetToken.markAsUsed();
        tokenRepo.update(usedToken);

        log.info("[PASSWORD_RESET] Success userId={}", user.getId());

        var confirmationResult =
                emailService.sendPasswordResetConfirmation(user.getEmail().value());
        if (confirmationResult.isLeft()) {
            log.error(
                    "[PASSWORD_RESET] Confirmation email failed userId={} error={}",
                    user.getId(),
                    confirmationResult.getLeft());
            // Don't fail the use case
        }

        return Either.right(new ResetResult.Success(user.getId()));
    }

    public sealed interface ResetError {
        record InvalidToken() implements ResetError {}

        record TokenExpired() implements ResetError {}

        record TokenAlreadyUsed() implements ResetError {}

        record WeakPassword(String reason) implements ResetError {}
    }

    public sealed interface ResetResult {
        record Success(UUID userId) implements ResetResult {}
    }
}
