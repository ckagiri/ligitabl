package com.ligitabl.api.usecase.account.setpassword;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.shared.Either;
import com.ligitabl.model.auth.Password;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.domain.service.PasswordHasher;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Gives a password to an account that has none (one created through Google sign-in). */
@Service
@RequiredArgsConstructor
@Slf4j
public class SetPasswordUseCase {

    private final UserRepo userRepo;
    private final PasswordHasher passwordHasher;

    @Transactional
    public Either<SetPasswordError, User> execute(UUID userId, String newPassword) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            return Either.left(new SetPasswordError.UserNotFound(userId));
        }
        if (user.getPassword() != null) {
            return Either.left(new SetPasswordError.AlreadySet(userId));
        }

        Password.Plaintext plaintext;
        try {
            plaintext = Password.Plaintext.create(newPassword);
        } catch (IllegalArgumentException e) {
            return Either.left(new SetPasswordError.InvalidPassword(e.getMessage()));
        }

        Password.Hashed hashed = passwordHasher.hash(plaintext);
        userRepo.updatePassword(userId, hashed);
        User updated = user.withPassword(hashed);

        log.info("[PASSWORD_SET] userId={}", userId);
        return Either.right(updated);
    }
}
