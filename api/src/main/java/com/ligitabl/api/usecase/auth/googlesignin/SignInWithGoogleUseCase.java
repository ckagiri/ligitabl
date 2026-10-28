package com.ligitabl.api.usecase.auth.googlesignin;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.usecase.shared.AdminAlerts;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.domain.service.PublicIdGenerator;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves a Google sign-in to a user: the one already linked to this Google subject, else the
 * account with the same email (linking it), else a new account.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignInWithGoogleUseCase {

    private final UserRepo userRepo;
    private final PublicIdGenerator publicIdGenerator;
    private final AdminAlerts adminAlerts;
    private final Clock clock;

    @Transactional
    public Either<SignInWithGoogleError, User> execute(GoogleIdentity identity) {
        var linked = userRepo.findByGoogleId(identity.subject());
        if (linked.isPresent()) {
            return Either.right(linked.get());
        }

        Email email = Email.create(identity.email().trim().toLowerCase(Locale.ROOT));
        var existing = userRepo.findByEmail(email);
        if (existing.isPresent()) {
            return linkExisting(existing.get(), identity.subject());
        }
        return Either.right(create(email, identity));
    }

    private Either<SignInWithGoogleError, User> linkExisting(User existing, String subject) {
        if (existing.getGoogleId() != null && !existing.getGoogleId().equals(subject)) {
            return Either.left(new SignInWithGoogleError.EmailLinkedToOtherGoogleAccount(
                    existing.getEmail().value()));
        }
        if (existing.getGoogleId() != null) {
            return Either.right(existing);
        }

        // Google has verified the address, which is what makes linking by email safe.
        User linked = existing.withEmailVerified(true).withGoogleId(subject);
        if (linked.getEmailVerifiedAt() == null) {
            linked = linked.withEmailVerifiedAt(OffsetDateTime.now(clock));
        }
        userRepo.update(linked);
        log.info(
                "[OAUTH_GOOGLE_LINKED] userId={} email={}",
                existing.getId(),
                existing.getEmail().value());
        return Either.right(linked);
    }

    private User create(Email email, GoogleIdentity identity) {
        UUID userId = UUID.randomUUID();
        String displayName =
                (identity.name() == null || identity.name().isBlank()) ? nameFromEmail(email.value()) : identity.name();

        User user = User.builder()
                .id(userId)
                .publicId(publicIdGenerator.generate(userId))
                .email(email)
                .displayName(displayName)
                .password(null)
                .roles(Set.of(Role.PLAYER))
                .emailVerified(true)
                .emailVerifiedAt(OffsetDateTime.now(clock))
                .googleId(identity.subject())
                .build();

        userRepo.create(user);
        log.info(
                "[OAUTH_GOOGLE_CREATED] userId={} email={}",
                user.getId(),
                user.getEmail().value());
        adminAlerts.notifyNewUserSignup(
                user.getPublicId().value(),
                user.getDisplayName(),
                user.getEmail().value(),
                "Google sign-in");
        return user;
    }

    /** "jane.doe_smith@x.com" → "Jane Doe Smith". */
    static String nameFromEmail(String email) {
        if (email == null || email.isBlank()) {
            return "Player";
        }
        String localPart = email.split("@")[0].replace(".", " ").replace("_", " ");
        StringBuilder result = new StringBuilder();
        for (String word : localPart.split("\\s+")) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        String name = result.toString().trim();
        return name.isBlank() ? "Player" : name;
    }
}
