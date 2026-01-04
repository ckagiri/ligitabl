package com.ligitabl.api.usecases.auth.register;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.shared.exceptions.UseCaseException;
import com.ligitabl.api.usecases.auth.AuthDto;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.Password;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class RegisterController {

    private final RegisterUseCase registerUserUseCase;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody AuthDto.RegisterRequest request) {
        log.debug("Registration request received for email: {}", request.email());

        return toRegisterCommand(request)
                .flatMap(registerUserUseCase::execute)
                .map(result -> ResponseEntity.status(HttpStatus.CREATED).body(toRegisterResponse(result)))
                .getOrElseThrow(error -> {
                    log.warn("Registration failed: {}", error.getMessage());
                    throw new UseCaseException(error);
                });
    }

    private Either<com.ligitabl.api.shared.errors.UseCaseError, RegisterCommand> toRegisterCommand(
            AuthDto.RegisterRequest request) {
        return Either.catching(() -> Email.create(request.email()), UseCaseErrors::fromException)
                .flatMap(email -> Either.catching(
                                () -> Password.Plaintext.create(request.password()), UseCaseErrors::fromException)
                        .map(password -> new RegisterCommand(email, request.displayName(), password)));
    }

    private AuthDto.RegisterResponse toRegisterResponse(RegisterResult result) {
        return new AuthDto.RegisterResponse(
                result.publicId().value(),
                result.email().value(),
                result.displayName(),
                result.roles().stream().map(r -> r.getValue()).collect(Collectors.toSet()));
    }
}
