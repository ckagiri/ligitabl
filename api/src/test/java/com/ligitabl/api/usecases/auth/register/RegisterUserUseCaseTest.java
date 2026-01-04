package com.ligitabl.api.usecases.auth.register;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.ConflictError;
import com.ligitabl.api.shared.errors.UnexpectedError;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.ValidationError;
import com.ligitabl.api.shared.validation.RequestValidator;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.Password;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.domain.service.PasswordHasher;
import com.ligitabl.model.domain.service.PublicIdGenerator;
import com.ligitabl.model.repo.UserRepo;

@ExtendWith(MockitoExtension.class)
@DisplayName("Register User Use Case")
class RegisterUserUseCaseTest {

    @Mock
    UserRepo userRepo;

    @Mock
    PasswordHasher passwordHasher;

    @Mock
    PublicIdGenerator publicIdGenerator;

    @Mock
    RequestValidator requestValidator;

    RegisterUserUseCase useCase;

    Email email;
    Password.Plaintext password;
    Password.Hashed hashed;
    PublicId publicId;

    @BeforeEach
    void setUp() {
        useCase = new RegisterHandler(userRepo, passwordHasher, publicIdGenerator, requestValidator);

        email = Email.create("newuser@example.com");
        password = Password.Plaintext.create("password123");
        hashed = Password.Hashed.of("$2a$10$hashedPassword");
        publicId = PublicId.create("AbCd3fGh9J");

        when(requestValidator.validate(any())).thenAnswer(inv -> Either.right(inv.getArgument(0)));
    }

    @Test
    @DisplayName("Should register user successfully with valid data")
    void shouldRegisterSuccessfully() {
        when(userRepo.existsByEmail(email)).thenReturn(false);
        when(passwordHasher.hash(password)).thenReturn(hashed);
        when(publicIdGenerator.generate(any(UUID.class))).thenReturn(publicId);
        when(userRepo.create(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var cmd = new RegisterCommand(email, "New User", password);

        Either<UseCaseError, RegisterResult> result = useCase.execute(cmd);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().publicId()).isEqualTo(publicId);
        assertThat(result.get().email()).isEqualTo(email);
        assertThat(result.get().displayName()).isEqualTo("New User");
        assertThat(result.get().roles()).containsExactly(Role.PLAYER);

        verify(userRepo).existsByEmail(email);
        verify(passwordHasher).hash(password);
        verify(publicIdGenerator).generate(any(UUID.class));
        verify(userRepo).create(any(User.class));
    }

    @Test
    @DisplayName("Should fail when email already exists")
    void shouldFailWhenEmailExists() {
        when(userRepo.existsByEmail(email)).thenReturn(true);

        var cmd = new RegisterCommand(email, "New User", password);

        Either<UseCaseError, RegisterResult> result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(ConflictError.class);
        assertThat(result.getLeft().getMessage()).contains(email.value());

        verify(userRepo).existsByEmail(email);
        verify(passwordHasher, never()).hash(any());
        verify(userRepo, never()).create(any());
    }

    @Test
    @DisplayName("Should hash password before saving")
    void shouldHashPassword() {
        when(userRepo.existsByEmail(email)).thenReturn(false);
        when(passwordHasher.hash(password)).thenReturn(hashed);
        when(publicIdGenerator.generate(any(UUID.class))).thenReturn(publicId);
        when(userRepo.create(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var cmd = new RegisterCommand(email, "Test User", password);

        useCase.execute(cmd);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).create(userCaptor.capture());

        assertThat(userCaptor.getValue().getPassword()).isEqualTo(hashed);
        verify(passwordHasher).hash(password);
    }

    @Test
    @DisplayName("Should assign PLAYER role by default")
    void shouldAssignDefaultRole() {
        when(userRepo.existsByEmail(email)).thenReturn(false);
        when(passwordHasher.hash(password)).thenReturn(hashed);
        when(publicIdGenerator.generate(any(UUID.class))).thenReturn(publicId);
        when(userRepo.create(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var cmd = new RegisterCommand(email, "Test User", password);

        Either<UseCaseError, RegisterResult> result = useCase.execute(cmd);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().roles()).containsExactly(Role.PLAYER);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).create(userCaptor.capture());
        assertThat(userCaptor.getValue().hasRole(Role.PLAYER)).isTrue();
    }

    @Test
    @DisplayName("Should set email as unverified by default")
    void shouldSetEmailUnverified() {
        when(userRepo.existsByEmail(email)).thenReturn(false);
        when(passwordHasher.hash(password)).thenReturn(hashed);
        when(publicIdGenerator.generate(any(UUID.class))).thenReturn(publicId);
        when(userRepo.create(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var cmd = new RegisterCommand(email, "Test User", password);

        useCase.execute(cmd);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).create(userCaptor.capture());
        assertThat(userCaptor.getValue().isEmailVerified()).isFalse();
    }

    @Test
    @DisplayName("Should trim display name whitespace")
    void shouldTrimDisplayName() {
        when(userRepo.existsByEmail(email)).thenReturn(false);
        when(passwordHasher.hash(password)).thenReturn(hashed);
        when(publicIdGenerator.generate(any(UUID.class))).thenReturn(publicId);
        when(userRepo.create(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var cmd = new RegisterCommand(email, "  Test User  ", password);

        Either<UseCaseError, RegisterResult> result = useCase.execute(cmd);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().displayName()).isEqualTo("Test User");
    }

    @Test
    @DisplayName("Should reject empty display name")
    void shouldRejectEmptyDisplayName() {
        var cmd = new RegisterCommand(email, "   ", password);

        Either<UseCaseError, RegisterResult> result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(ValidationError.class);
    }

    @Test
    @DisplayName("Should reject display name shorter than 2 characters")
    void shouldRejectShortDisplayName() {
        var cmd = new RegisterCommand(email, "A", password);

        Either<UseCaseError, RegisterResult> result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(ValidationError.class);
    }

    @Test
    @DisplayName("Should reject display name longer than 100 characters")
    void shouldRejectLongDisplayName() {
        String longName = "A".repeat(101);
        var cmd = new RegisterCommand(email, longName, password);

        Either<UseCaseError, RegisterResult> result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(ValidationError.class);
    }

    @Test
    @DisplayName("Should handle repository create errors")
    void shouldHandleRepositoryCreateErrors() {
        when(userRepo.existsByEmail(email)).thenReturn(false);
        when(passwordHasher.hash(password)).thenReturn(hashed);
        when(publicIdGenerator.generate(any(UUID.class))).thenReturn(publicId);
        when(userRepo.create(any(User.class))).thenThrow(new RuntimeException("Database error"));

        var cmd = new RegisterCommand(email, "Test User", password);

        Either<UseCaseError, RegisterResult> result = useCase.execute(cmd);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(UnexpectedError.class);
    }
}
