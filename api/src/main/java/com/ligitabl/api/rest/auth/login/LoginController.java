package com.ligitabl.api.rest.auth.login;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.rest.auth.AuthDto;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.*;
import com.ligitabl.api.shared.exceptions.UseCaseException;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.Password;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class LoginController {
    private final LoginUseCase loginUseCase;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AuthDto.LoginRequest request) {
        log.debug("Login request received for email: {}", request.email());

        return toLoginCommand(request)
                .flatMap(loginUseCase::execute)
                .map(result -> ResponseEntity.ok(toLoginResponse(result)))
                .getOrElseThrow(error -> {
                    log.warn("Login failed: {}", error.getMessage());
                    throw new UseCaseException(error);
                });
    }

    public Either<UseCaseError, LoginCommand> toLoginCommand(AuthDto.LoginRequest request) {
        return Either.catching(() -> Email.create(request.email()), UseCaseErrors::fromException)
                .flatMap(email -> Either.catching(
                                () -> Password.Plaintext.create(request.password()), UseCaseErrors::fromException)
                        .map(password -> new LoginCommand(email, password)));
    }

    private AuthDto.LoginResponse toLoginResponse(LoginResult result) {
        return new AuthDto.LoginResponse(result.accessToken());
    }
}
