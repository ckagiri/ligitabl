package com.ligitabl.api.usecases.auth.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
class LoginUseCaseTest {

    @Mock
    UserRepo userRepo;

    @Mock
    PasswordHasher passwordHasher;

    @Mock
    TokenGenerator tokenGenerator;

    @Mock
    RequestValidator requestValidator;

    LoginUseCase loginUseCase;

    Email email;
    Password.Plaintext password;
    User user;

    @BeforeEach
    void setUp() {
        loginUseCase = new LoginHandler(userRepo, passwordHasher, tokenGenerator, requestValidator);

        email = Email.create("test@example.com");
        password = Password.Plaintext.create("password123");

        user = User.builder()
                .id(UUID.randomUUID())
                .publicId(PublicId.create("AbCd3fGh9J"))
                .email(email)
                .displayName("Test User")
                .password(Password.Hashed.of("$2a$10$hashedPassword"))
                .roles(Set.of(Role.PLAYER))
                .emailVerified(true)
                .build();
    }

    @Test
    void shouldLoginSuccessfullyWithValidCredentials() {
        var cmd = new LoginCommand(email, password);

        when(requestValidator.validate(cmd)).thenReturn(Either.right(cmd));
        when(userRepo.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordHasher.verify(password, user.getPassword())).thenReturn(true);
        when(tokenGenerator.generateAccessToken(user.getPublicId(), user.getRoles())).thenReturn("valid-jwt-token");

        Either<UseCaseError, LoginResult> result = loginUseCase.execute(cmd);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().accessToken()).isEqualTo("valid-jwt-token");

        verify(userRepo).findByEmail(email);
        verify(passwordHasher).verify(password, user.getPassword());
        verify(tokenGenerator).generateAccessToken(user.getPublicId(), user.getRoles());
    }

    @Test
    void shouldFailWhenUserNotFound() {
        var cmd = new LoginCommand(email, password);

        when(requestValidator.validate(cmd)).thenReturn(Either.right(cmd));
        when(userRepo.findByEmail(email)).thenReturn(Optional.empty());

        Either<UseCaseError, LoginResult> result = loginUseCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(AuthorizationError.class);

        verify(userRepo).findByEmail(email);
        verify(passwordHasher, never()).verify(any(), any());
        verify(tokenGenerator, never()).generateAccessToken(any(), any());
    }

    @Test
    void shouldFailWhenPasswordInvalid() {
        var cmd = new LoginCommand(email, password);

        when(requestValidator.validate(cmd)).thenReturn(Either.right(cmd));
        when(userRepo.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordHasher.verify(password, user.getPassword())).thenReturn(false);

        Either<UseCaseError, LoginResult> result = loginUseCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(AuthorizationError.class);

        verify(userRepo).findByEmail(email);
        verify(passwordHasher).verify(password, user.getPassword());
        verify(tokenGenerator, never()).generateAccessToken(any(), any());
    }

    @Test
    void shouldGenerateTokenWithCorrectClaims() {
        User multiRoleUser = user.withRoles(Set.of(Role.ADMIN, Role.PLAYER));
        var cmd = new LoginCommand(email, password);

        when(requestValidator.validate(cmd)).thenReturn(Either.right(cmd));
        when(userRepo.findByEmail(email)).thenReturn(Optional.of(multiRoleUser));
        when(passwordHasher.verify(password, multiRoleUser.getPassword())).thenReturn(true);
        when(tokenGenerator.generateAccessToken(multiRoleUser.getPublicId(), multiRoleUser.getRoles()))
                .thenReturn("admin-token");

        Either<UseCaseError, LoginResult> result = loginUseCase.execute(cmd);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().accessToken()).isEqualTo("admin-token");

        verify(tokenGenerator).generateAccessToken(multiRoleUser.getPublicId(), Set.of(Role.ADMIN, Role.PLAYER));
    }
}
