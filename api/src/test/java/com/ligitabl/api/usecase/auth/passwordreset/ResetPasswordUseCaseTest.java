package com.ligitabl.api.usecase.auth.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.testsupport.TestClock;
import com.ligitabl.model.domain.identity.Email;
import com.ligitabl.model.domain.identity.Password;
import com.ligitabl.model.domain.identity.PasswordHasher;
import com.ligitabl.model.domain.identity.PasswordResetToken;
import com.ligitabl.model.domain.identity.Role;
import com.ligitabl.model.domain.identity.User;
import com.ligitabl.model.repo.PasswordResetTokenRepo;
import com.ligitabl.model.repo.UserRepo;

/**
 * The password-reset twin of {@link VerifyEmailUseCaseTest}: {@code PasswordResetToken} mirrors
 * {@code EmailVerificationToken} line for line, including the boundary case below.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Reset Password Use Case")
class ResetPasswordUseCaseTest {

    private static final String NEW_PASSWORD = "Str0ng-Enough!";

    @Mock
    UserRepo userRepo;

    @Mock
    PasswordResetTokenRepo tokenRepo;

    @Mock
    PasswordHasher passwordHasher;

    @Mock
    PasswordResetEmailService emailService;

    ResetPasswordUseCase useCase;

    UUID userId;
    User user;

    @BeforeEach
    void setUp() {
        useCase = new ResetPasswordUseCase(userRepo, tokenRepo, passwordHasher, emailService, TestClock.FIXED);

        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .email(Email.create("player@example.com"))
                .displayName("Player")
                .roles(Set.of(Role.PLAYER))
                .build();
    }

    @Test
    @DisplayName("Should reject null or blank tokens without touching the repos")
    void shouldRejectBlankToken() {
        assertThat(useCase.execute(null, NEW_PASSWORD).getLeft()).isInstanceOf(ResetPasswordError.InvalidToken.class);
        assertThat(useCase.execute("  ", NEW_PASSWORD).getLeft()).isInstanceOf(ResetPasswordError.InvalidToken.class);
        verifyNoInteractions(tokenRepo, userRepo, passwordHasher, emailService);
    }

    @Test
    @DisplayName("Should reject a weak password before looking the token up")
    void shouldRejectWeakPassword() {
        // Ordering matters: a weak password must not consume the token, or a user who fat-fingers
        // their new password has to request a fresh reset email.
        var result = useCase.execute("some-token", "short");

        assertThat(result.getLeft()).isInstanceOf(ResetPasswordError.WeakPassword.class);
        verifyNoInteractions(tokenRepo, userRepo, passwordHasher);
    }

    @Test
    @DisplayName("Should reject unknown tokens")
    void shouldRejectUnknownToken() {
        when(tokenRepo.findByToken("nope")).thenReturn(Optional.empty());

        assertThat(useCase.execute("nope", NEW_PASSWORD).getLeft()).isInstanceOf(ResetPasswordError.InvalidToken.class);
        verifyNoInteractions(userRepo, passwordHasher);
    }

    @Test
    @DisplayName("Should reject already-used tokens")
    void shouldRejectUsedToken() {
        PasswordResetToken used = liveToken().markAsUsed(TestClock.NOW);
        when(tokenRepo.findByToken(used.getToken())).thenReturn(Optional.of(used));

        assertThat(useCase.execute(used.getToken(), NEW_PASSWORD).getLeft())
                .isInstanceOf(ResetPasswordError.TokenAlreadyUsed.class);
        verify(userRepo, never()).updatePassword(any(), any());
    }

    @Test
    @DisplayName("Should reject expired tokens")
    void shouldRejectExpiredToken() {
        PasswordResetToken stale = tokenExpiringAt(TestClock.NOW.minus(1, ChronoUnit.HOURS));
        when(tokenRepo.findByToken(stale.getToken())).thenReturn(Optional.of(stale));

        assertThat(useCase.execute(stale.getToken(), NEW_PASSWORD).getLeft())
                .isInstanceOf(ResetPasswordError.TokenExpired.class);
        verify(userRepo, never()).updatePassword(any(), any());
        verify(tokenRepo, never()).update(any());
    }

    @Test
    @DisplayName("Should treat a token expiring at this exact instant as still live")
    void shouldTreatExactExpiryAsNotYetExpired() {
        // `isExpired` is strict (`at.isAfter`): a token expiring exactly now is valid and one second
        // later is not, pinning the strictness as chosen.
        PasswordResetToken atTheBoundary = tokenExpiringAt(TestClock.NOW);
        when(tokenRepo.findByToken(atTheBoundary.getToken())).thenReturn(Optional.of(atTheBoundary));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHasher.hash(any())).thenReturn(new Password.Hashed("hashed"));
        when(emailService.sendPasswordResetConfirmation(any())).thenReturn(Either.right(null));

        assertThat(useCase.execute(atTheBoundary.getToken(), NEW_PASSWORD).isRight())
                .isTrue();

        PasswordResetToken aSecondStale = tokenExpiringAt(TestClock.NOW.minusSeconds(1));
        when(tokenRepo.findByToken(aSecondStale.getToken())).thenReturn(Optional.of(aSecondStale));

        assertThat(useCase.execute(aSecondStale.getToken(), NEW_PASSWORD).getLeft())
                .isInstanceOf(ResetPasswordError.TokenExpired.class);
    }

    @Test
    @DisplayName("Should reject when the token's user no longer exists, without consuming the token")
    void shouldRejectWhenUserMissing() {
        PasswordResetToken token = liveToken();
        when(tokenRepo.findByToken(token.getToken())).thenReturn(Optional.of(token));
        when(userRepo.findById(userId)).thenReturn(Optional.empty());

        assertThat(useCase.execute(token.getToken(), NEW_PASSWORD).getLeft())
                .isInstanceOf(ResetPasswordError.InvalidToken.class);
        verify(userRepo, never()).updatePassword(any(), any());
        verify(tokenRepo, never()).update(any());
    }

    @Test
    @DisplayName("Should update the password and stamp the token used at the evaluated instant")
    void shouldResetSuccessfully() {
        PasswordResetToken token = liveToken();
        Password.Hashed hashed = new Password.Hashed("hashed");
        when(tokenRepo.findByToken(token.getToken())).thenReturn(Optional.of(token));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHasher.hash(any())).thenReturn(hashed);
        when(emailService.sendPasswordResetConfirmation("player@example.com")).thenReturn(Either.right(null));

        var result = useCase.execute(token.getToken(), NEW_PASSWORD);

        assertThat(result.isRight()).isTrue();
        assertThat(result.get()).isEqualTo(new ResetPasswordResult.Success(userId));
        verify(userRepo).updatePassword(userId, hashed);

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepo).update(captor.capture());
        assertThat(captor.getValue().isUsed()).isTrue();
        // The exact instant, not merely non-null: the use case reads its clock once and reuses it
        // for the validity branch and this stamp, so a second read creeping back in shows up here.
        assertThat(captor.getValue().getUsedAt()).isEqualTo(TestClock.NOW);
    }

    @Test
    @DisplayName("Should still succeed when the confirmation email fails")
    void shouldSucceedWhenConfirmationEmailFails() {
        // The password is already changed by this point; failing the use case would tell the user
        // their reset didn't work when it did.
        PasswordResetToken token = liveToken();
        when(tokenRepo.findByToken(token.getToken())).thenReturn(Optional.of(token));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(passwordHasher.hash(any())).thenReturn(new Password.Hashed("hashed"));
        when(emailService.sendPasswordResetConfirmation(any()))
                .thenReturn(Either.left(
                        new com.ligitabl.api.usecase.shared.email.EmailError.EmailProviderError("provider down")));

        assertThat(useCase.execute(token.getToken(), NEW_PASSWORD).isRight()).isTrue();
        verify(tokenRepo).update(any());
    }

    private PasswordResetToken liveToken() {
        return PasswordResetToken.create(userId, 30, TestClock.NOW);
    }

    private PasswordResetToken tokenExpiringAt(Instant expiresAt) {
        return PasswordResetToken.builder()
                .token(UUID.randomUUID().toString())
                .userId(userId)
                .createdAt(expiresAt.minus(30, ChronoUnit.MINUTES))
                .expiresAt(expiresAt)
                .used(false)
                .usedAt(null)
                .build();
    }
}
