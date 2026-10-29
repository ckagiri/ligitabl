package com.ligitabl.api.usecase.auth.login;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.shared.Either;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.Password;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.domain.service.PasswordHasher;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;

/** The email-and-password check shared by web login and the JSON {@link LoginUseCase}. */
@Service
@RequiredArgsConstructor
public class VerifyCredentialsUseCase {

    private final UserRepo userRepo;
    private final PasswordHasher passwordHasher;

    @Transactional(readOnly = true)
    public Either<VerifyCredentialsError, User> execute(Email email, Password.Plaintext password) {
        return userRepo.findByEmail(email)
                .filter(user -> passwordHasher.verify(password, user.getPassword()))
                .<Either<VerifyCredentialsError, User>>map(Either::right)
                .orElseGet(() -> Either.left(new VerifyCredentialsError.InvalidCredentials()));
    }
}
