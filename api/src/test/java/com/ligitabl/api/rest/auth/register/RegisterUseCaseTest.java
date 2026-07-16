package com.ligitabl.api.rest.auth.register;

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
import com.ligitabl.api.web.auth.RequestEmailVerificationUseCase;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.Password;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.domain.service.PasswordHasher;
import com.ligitabl.model.domain.service.PublicIdGenerator;
import com.ligitabl.model.repo.UserRepo;

import jakarta.validation.Validation;

@ExtendWith(MockitoExtension.class)
@DisplayName("Register User Use Case")
class RegisterUseCaseTest {

    @Mock
    UserRepo userRepo;

    @Mock
    PasswordHasher passwordHasher;

    @Mock
    PublicIdGenerator publicIdGenerator;

    @Mock
    RequestEmailVerificationUseCase requestEmailVerificationUseCase;

    RequestValidator requestValidator;

    RegisterUseCase useCase;

    Email email;
    Password.Plaintext password;
    Password.Hashed hashed;
    PublicId publicId;

    @BeforeEach
    void setUp() {
        requestValidator =
                new RequestValidator(Validation.buildDefaultValidatorFactory().getValidator());
        useCase = new RegisterUseCase(
                userRepo, passwordHasher, publicIdGenerator, requestValidator, requestEmailVerificationUseCase);

        email = Email.create("newuser@example.com");
        password = Password.Plaintext.create("password123");
        hashed = Password.Hashed.of("$2a$10$hashedPassword");
        publicId = PublicId.create("AbCd3fGh9J");
    }

    /** Only for tests that reach the post-create hook — keeps stubbing strict everywhere else. */
    private void stubVerificationEmailSuccess() {
        when(requestEmailVerificationUseCase.execute(any(UUID.class)))
                .thenReturn(Either.right(new RequestEmailVerificationUseCase.RequestResult.EmailSent(email.value())));
    }

    @Test
    @DisplayName("Should register user successfully with valid data")
    void shouldRegisterSuccessfully() {
        when(userRepo.existsByEmail(email)).thenReturn(false);
        when(passwordHasher.hash(password)).thenReturn(hashed);
        when(publicIdGenerator.generate(any(UUID.class))).thenReturn(publicId);
        when(userRepo.create(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        stubVerificationEmailSuccess();

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
        stubVerificationEmailSuccess();

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
        stubVerificationEmailSuccess();

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
        stubVerificationEmailSuccess();

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
        stubVerificationEmailSuccess();

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

    @Test
    @DisplayName("Should send verification email after successful registration")
    void shouldSendVerificationEmailAfterRegistration() {
        when(userRepo.existsByEmail(email)).thenReturn(false);
        when(passwordHasher.hash(password)).thenReturn(hashed);
        when(publicIdGenerator.generate(any(UUID.class))).thenReturn(publicId);
        when(userRepo.create(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        stubVerificationEmailSuccess();

        var cmd = new RegisterCommand(email, "Test User", password);

        useCase.execute(cmd);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).create(userCaptor.capture());
        verify(requestEmailVerificationUseCase).execute(userCaptor.getValue().getId());
    }

    @Test
    @DisplayName("Should register successfully even when verification email fails")
    void shouldRegisterWhenVerificationEmailFails() {
        when(userRepo.existsByEmail(email)).thenReturn(false);
        when(passwordHasher.hash(password)).thenReturn(hashed);
        when(publicIdGenerator.generate(any(UUID.class))).thenReturn(publicId);
        when(userRepo.create(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(requestEmailVerificationUseCase.execute(any(UUID.class)))
                .thenReturn(Either.left(new RequestEmailVerificationUseCase.RequestError.UnexpectedError()));

        var cmd = new RegisterCommand(email, "Test User", password);

        Either<UseCaseError, RegisterResult> result = useCase.execute(cmd);

        assertThat(result.isRight()).isTrue();
    }

    @Test
    @DisplayName("Should register successfully even when verification email throws")
    void shouldRegisterWhenVerificationEmailThrows() {
        when(userRepo.existsByEmail(email)).thenReturn(false);
        when(passwordHasher.hash(password)).thenReturn(hashed);
        when(publicIdGenerator.generate(any(UUID.class))).thenReturn(publicId);
        when(userRepo.create(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(requestEmailVerificationUseCase.execute(any(UUID.class))).thenThrow(new RuntimeException("boom"));

        var cmd = new RegisterCommand(email, "Test User", password);

        Either<UseCaseError, RegisterResult> result = useCase.execute(cmd);

        assertThat(result.isRight()).isTrue();
    }

    @Test
    @DisplayName("Should not send verification email when registration fails")
    void shouldNotSendVerificationEmailWhenCreateFails() {
        when(userRepo.existsByEmail(email)).thenReturn(true);

        var cmd = new RegisterCommand(email, "Test User", password);

        useCase.execute(cmd);

        verify(requestEmailVerificationUseCase, never()).execute(any(UUID.class));
    }
}
