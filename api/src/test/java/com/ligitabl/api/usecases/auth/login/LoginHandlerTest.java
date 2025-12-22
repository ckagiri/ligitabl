package com.ligitabl.api.usecases.auth.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.auth.security.TokenGenerator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.AuthorizationError;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.Password;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.domain.service.PasswordHasher;
import com.ligitabl.model.repo.UserRepo;

@ExtendWith(MockitoExtension.class)
class LoginHandlerTest {

    @Mock
    UserRepo userRepo;

    @Mock
    PasswordHasher passwordHasher;

    @Mock
    TokenGenerator tokenGenerator;

    @Mock
    RequestValidator requestValidator;

    LoginUseCase useCase;

    Email email;
    Password.Plaintext password;
    User user;

    @BeforeEach
    void setup() {
        useCase = new LoginHandler(userRepo, passwordHasher, tokenGenerator, requestValidator);

        email = Email.create("admin@example.com");
        password = Password.Plaintext.create("admin12345");

        user = User.builder()
                .id(UUID.randomUUID())
                .publicId(PublicId.create("AbCd3fGh9J"))
                .email(email)
                .password(Password.Hashed.of("$2a$10$hash"))
                .displayName("Admin")
                .roles(Set.of(Role.ADMIN))
                .emailVerified(true)
                .build();
    }

    @Test
    void valid_credentials_returns_token() {
        var cmd = new LoginCommand(email, password);

        when(requestValidator.validate(cmd)).thenReturn(Either.right(cmd));
        when(userRepo.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordHasher.verify(password, user.getPassword())).thenReturn(true);
        when(tokenGenerator.generateAccessToken(user.getPublicId(), user.getRoles()))
                .thenReturn("token123");

        Either<UseCaseError, LoginResult> result = useCase.execute(cmd);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().accessToken()).isEqualTo("token123");
        verify(tokenGenerator).generateAccessToken(user.getPublicId(), user.getRoles());
    }

    @Test
    void invalid_password_returns_unauthorized() {
        var cmd = new LoginCommand(email, password);

        when(requestValidator.validate(cmd)).thenReturn(Either.right(cmd));
        when(userRepo.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordHasher.verify(password, user.getPassword())).thenReturn(false);

        Either<UseCaseError, LoginResult> result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(AuthorizationError.class);
        verify(tokenGenerator, never()).generateAccessToken(any(), any());
    }
}
