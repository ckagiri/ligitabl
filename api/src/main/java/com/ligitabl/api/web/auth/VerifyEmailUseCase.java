package com.ligitabl.api.web.auth;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.shared.Either;
import com.ligitabl.model.repo.EmailVerificationTokenRepo;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerifyEmailUseCase {
    private final UserRepo userRepo;
    private final EmailVerificationTokenRepo tokenRepo;

    @Transactional
    public Either<VerifyError, VerifyResult> execute(String token) {
        if (token == null || token.isBlank()) {
            return Either.left(new VerifyError.InvalidToken());
        }

        var tokenResult = tokenRepo.findByToken(token);
        if (tokenResult.isEmpty()) {
            log.warn("[EMAIL_VERIFICATION] Token not found");
            return Either.left(new VerifyError.InvalidToken());
        }

        var verificationToken = tokenResult.get();
        if (!verificationToken.isValid()) {
            log.warn(
                    "[EMAIL_VERIFICATION] Token invalid: used={} expired={}",
                    verificationToken.isUsed(),
                    verificationToken.isExpired());

            if (verificationToken.isUsed()) {
                return Either.left(new VerifyError.TokenAlreadyUsed());
            } else {
                return Either.left(new VerifyError.TokenExpired());
            }
        }

        var userResult = userRepo.findById(verificationToken.getUserId());
        if (userResult.isEmpty()) {
            log.error("[EMAIL_VERIFICATION] User not found userId={}", verificationToken.getUserId());
            return Either.left(new VerifyError.InvalidToken());
        }

        var user = userResult.get();

        tokenRepo.update(verificationToken.markAsUsed());
        userRepo.markEmailVerified(user.getId(), OffsetDateTime.now());

        log.info("[EMAIL_VERIFICATION] Success userId={}", user.getId());

        return Either.right(
                new VerifyResult.Success(user.getId(), user.getEmail().value()));
    }

    public sealed interface VerifyError {
        record InvalidToken() implements VerifyError {}

        record TokenExpired() implements VerifyError {}

        record TokenAlreadyUsed() implements VerifyError {}
    }

    public sealed interface VerifyResult {
        record Success(UUID userId, String email) implements VerifyResult {}
    }
}
