package com.ligitabl.api.usecase.account.googlelink;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.identity.User;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UnlinkGoogleUseCase {

    private final UserRepo userRepo;

    @Transactional
    public Either<UnlinkGoogleError, User> execute(UUID userId) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            return Either.left(new UnlinkGoogleError.UserNotFound(userId));
        }
        if (!user.isGoogleLinked()) {
            return Either.left(new UnlinkGoogleError.NotLinked(userId));
        }
        if (!user.canUnlinkGoogle()) {
            return Either.left(new UnlinkGoogleError.PasswordRequired(userId));
        }

        userRepo.unlinkGoogle(userId);

        log.info("[GOOGLE_UNLINKED] userId={}", userId);
        return Either.right(user.withGoogleId(null));
    }
}
