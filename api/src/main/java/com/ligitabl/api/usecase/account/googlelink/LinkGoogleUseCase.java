package com.ligitabl.api.usecase.account.googlelink;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Links a Google account to a signed-in user from the profile page. */
@Service
@RequiredArgsConstructor
@Slf4j
public class LinkGoogleUseCase {

    private final UserRepo userRepo;
    private final Clock clock;

    @Transactional
    public Either<LinkGoogleError, User> execute(UUID userId, String googleSubject) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            return Either.left(new LinkGoogleError.UserNotFound(userId));
        }

        var other = userRepo.findByGoogleId(googleSubject)
                .filter(found -> !found.getId().equals(userId));
        if (other.isPresent()) {
            log.warn(
                    "[GOOGLE_ALREADY_LINKED] googleId={} already linked to userId={}",
                    googleSubject,
                    other.get().getId());
            return Either.left(
                    new LinkGoogleError.LinkedToAnotherUser(other.get().getId()));
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        userRepo.linkGoogle(userId, googleSubject, now);
        User updated = user.withEmailVerified(true).withGoogleId(googleSubject);
        if (updated.getEmailVerifiedAt() == null) {
            updated = updated.withEmailVerifiedAt(now);
        }
        log.info("[GOOGLE_LINKED_SUCCESS] userId={} googleId={}", userId, googleSubject);
        return Either.right(updated);
    }
}
