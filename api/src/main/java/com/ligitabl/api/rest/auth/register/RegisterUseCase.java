package com.ligitabl.api.rest.auth.register;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.notification.AdminNotificationService;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.api.web.auth.RequestEmailVerificationUseCase;
import com.ligitabl.model.auth.Password;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.domain.service.PasswordHasher;
import com.ligitabl.model.domain.service.PublicIdGenerator;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RegisterUseCase implements UseCase<RegisterCommand, Either<UseCaseError, RegisterResult>> {

    private final UserRepo userRepo;
    private final PasswordHasher passwordHasher;
    private final PublicIdGenerator publicIdGenerator;
    private final RequestValidator requestValidator;
    private final RequestEmailVerificationUseCase requestEmailVerificationUseCase;
    private final AdminNotificationService adminNotificationService;

    @Override
    public Either<UseCaseError, RegisterResult> execute(RegisterCommand command) {
        RegisterCommand normalized = normalize(command);

        return requestValidator.validate(normalized).flatMap(cmd -> {
            if (userRepo.existsByEmail(cmd.email())) {
                return Either.left(
                        UseCaseErrors.conflict("User with email '" + cmd.email().value() + "' already exists"));
            }

            UUID userId = UUID.randomUUID();
            Password.Hashed hashedPassword = passwordHasher.hash(cmd.password());

            User user = User.builder()
                    .id(userId)
                    .publicId(publicIdGenerator.generate(userId))
                    .email(cmd.email())
                    .displayName(cmd.displayName())
                    .password(hashedPassword)
                    .roles(Set.of(Role.PLAYER))
                    .emailVerified(false)
                    .build();

            return Either.catching(() -> userRepo.create(user), UseCaseErrors::fromException)
                    .map(saved -> {
                        // Registration counts as the first login
                        userRepo.updateLastLoginAt(saved.getId(), OffsetDateTime.now());
                        sendVerificationEmail(saved.getId());
                        log.info("User registered successfully: {}", saved.getPublicId());
                        adminNotificationService.notifyNewUserSignup(
                                saved.getPublicId().value(),
                                saved.getDisplayName(),
                                saved.getEmail().value(),
                                "email registration");
                        return new RegisterResult(
                                saved.getPublicId(), saved.getEmail(), saved.getDisplayName(), saved.getRoles());
                    });
        });
    }

    /**
     * Verification email must never fail registration — the user can always
     * resend from the profile page.
     */
    private void sendVerificationEmail(UUID userId) {
        try {
            requestEmailVerificationUseCase
                    .execute(userId)
                    .peekLeft(error -> log.warn(
                            "[EMAIL_VERIFICATION] Post-registration send failed userId={} error={}", userId, error));
        } catch (Exception e) {
            log.warn("[EMAIL_VERIFICATION] Post-registration send failed userId={}", userId, e);
        }
    }

    private static RegisterCommand normalize(RegisterCommand command) {
        String name = command.displayName();
        String trimmed = name == null ? null : name.trim();
        return new RegisterCommand(command.email(), trimmed, command.password());
    }
}
