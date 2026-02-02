package com.ligitabl.api.rest.auth.login;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.auth.security.TokenGenerator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.model.domain.service.PasswordHasher;
import com.ligitabl.model.repo.UserRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class LoginUseCase implements UseCase<LoginCommand, Either<UseCaseError, LoginResult>> {

    private static final UseCaseError INVALID_CREDENTIALS = UseCaseErrors.unauthorized("Invalid credentials");

    private final UserRepo userRepo;
    private final PasswordHasher passwordHasher;
    private final TokenGenerator tokenGenerator;
    private final RequestValidator requestValidator;

    @Override
    public Either<UseCaseError, LoginResult> execute(LoginCommand command) {
        return requestValidator
                .validate(command)
                .flatMap(cmd -> requireFound(userRepo.findByEmail(cmd.email()), INVALID_CREDENTIALS))
                .filterOrElse(
                        user -> passwordHasher.verify(command.password(), user.getPassword()),
                        () -> INVALID_CREDENTIALS)
                .map(user -> {
                    String token = tokenGenerator.generateAccessToken(user.getPublicId(), user.getRoles());
                    log.info("User logged in successfully: {}", user.getPublicId());
                    return new LoginResult(token);
                });
    }
}
