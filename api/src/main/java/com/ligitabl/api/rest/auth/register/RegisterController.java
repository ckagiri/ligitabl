package com.ligitabl.api.rest.auth.register;

import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ligitabl.api.client.TurnstileClient;
import com.ligitabl.api.rest.auth.AuthDto;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.shared.exceptions.UseCaseException;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.Password;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class RegisterController {

    private final RegisterUseCase registerUserUseCase;
    private final TurnstileClient turnstileClient;

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @Valid @RequestBody AuthDto.RegisterRequest request, HttpServletRequest httpRequest) {
        log.debug("Registration request received for email: {}", request.email());

        return verifyTurnstile(request, httpRequest)
                .flatMap(verified -> toRegisterCommand(request))
                .flatMap(registerUserUseCase::execute)
                .map(result -> ResponseEntity.status(HttpStatus.CREATED).body(toRegisterResponse(result)))
                .getOrElseThrow(error -> {
                    log.warn("Registration failed: {}", error.getMessage());
                    throw new UseCaseException(error);
                });
    }

    /**
     * Never logs the token itself — only the failure reason.
     */
    private Either<UseCaseError, Boolean> verifyTurnstile(
            AuthDto.RegisterRequest request, HttpServletRequest httpRequest) {
        if (!turnstileClient.isEnabled()) {
            return Either.right(Boolean.TRUE);
        }
        return turnstileClient
                .verify(request.turnstileToken(), httpRequest.getRemoteAddr())
                .peekLeft(err -> log.warn("[TURNSTILE_VERIFY_FAILED] reason={}", err))
                .mapLeft(err ->
                        (UseCaseError) UseCaseErrors.unprocessableEntity("Verification failed, please try again"))
                .map(response -> Boolean.TRUE);
    }

    private Either<UseCaseError, RegisterCommand> toRegisterCommand(AuthDto.RegisterRequest request) {
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
