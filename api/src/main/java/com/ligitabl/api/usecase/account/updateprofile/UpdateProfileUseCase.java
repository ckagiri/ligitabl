package com.ligitabl.api.usecase.account.updateprofile;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateProfileUseCase {

    private final UserRepo userRepo;

    /** Returns the updated user so the caller can refresh what it holds in the session. */
    @Transactional
    public Either<UpdateProfileError, User> execute(UUID userId, String displayName, boolean receiveResultsEmail) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            return Either.left(new UpdateProfileError.UserNotFound(userId));
        }

        // Stored inverted: the column is an opt-out.
        User updated = user.withDisplayName(displayName).withResultsEmailOptOut(!receiveResultsEmail);
        userRepo.update(updated);

        log.info("[PROFILE_UPDATED] userId={} newDisplayName={}", userId, displayName);
        return Either.right(updated);
    }
}
