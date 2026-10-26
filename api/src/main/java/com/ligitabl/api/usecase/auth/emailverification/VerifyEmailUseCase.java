package com.ligitabl.api.usecase.auth.emailverification;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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
    private final Clock clock;

    @Transactional
    public Either<VerifyEmailError, VerifyEmailResult> execute(String token) {
        if (token == null || token.isBlank()) {
            return Either.left(new VerifyEmailError.InvalidToken());
        }

        var tokenResult = tokenRepo.findByToken(token);
        if (tokenResult.isEmpty()) {
            log.warn("[EMAIL_VERIFICATION] Token not found");
            return Either.left(new VerifyEmailError.InvalidToken());
        }

        // One read, reused for the validity branch, the used-at stamp and emailVerifiedAt: separate
        // reads could straddle the expiry boundary, and could stamp the user verified at an instant
        // before the token that verified them was marked used.
        Instant now = clock.instant();

        var verificationToken = tokenResult.get();
        if (!verificationToken.isValid(now)) {
            log.warn(
                    "[EMAIL_VERIFICATION] Token invalid: used={} expired={}",
                    verificationToken.isUsed(),
                    verificationToken.isExpired(now));

            if (verificationToken.isUsed()) {
                return Either.left(new VerifyEmailError.TokenAlreadyUsed());
            } else {
                return Either.left(new VerifyEmailError.TokenExpired());
            }
        }

        var userResult = userRepo.findById(verificationToken.getUserId());
        if (userResult.isEmpty()) {
            log.error("[EMAIL_VERIFICATION] User not found userId={}", verificationToken.getUserId());
            return Either.left(new VerifyEmailError.InvalidToken());
        }

        var user = userResult.get();

        tokenRepo.update(verificationToken.markAsUsed(now));
        userRepo.markEmailVerified(user.getId(), OffsetDateTime.ofInstant(now, ZoneOffset.UTC));

        log.info("[EMAIL_VERIFICATION] Success userId={}", user.getId());

        return Either.right(
                new VerifyEmailResult.Success(user.getId(), user.getEmail().value()));
    }
}
