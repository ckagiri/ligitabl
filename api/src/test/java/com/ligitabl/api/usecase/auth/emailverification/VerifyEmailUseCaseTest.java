package com.ligitabl.api.usecase.auth.emailverification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

import com.ligitabl.api.testsupport.TestClock;
import com.ligitabl.model.domain.identity.Email;
import com.ligitabl.model.domain.identity.EmailVerificationToken;
import com.ligitabl.model.domain.identity.Role;
import com.ligitabl.model.domain.identity.User;
import com.ligitabl.model.repo.EmailVerificationTokenRepo;
import com.ligitabl.model.repo.UserRepo;

@ExtendWith(MockitoExtension.class)
@DisplayName("Verify Email Use Case")
class VerifyEmailUseCaseTest {

    @Mock
    UserRepo userRepo;

    @Mock
    EmailVerificationTokenRepo tokenRepo;

    VerifyEmailUseCase useCase;

    UUID userId;
    User user;

    @BeforeEach
    void setUp() {
        useCase = new VerifyEmailUseCase(userRepo, tokenRepo, TestClock.FIXED);

        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .email(Email.create("player@example.com"))
                .displayName("Player")
                .roles(Set.of(Role.PLAYER))
                .emailVerified(false)
                .build();
    }

    @Test
    @DisplayName("Should reject null or blank tokens")
    void shouldRejectBlankToken() {
        assertThat(useCase.execute(null).getLeft()).isInstanceOf(VerifyEmailError.InvalidToken.class);
        assertThat(useCase.execute("  ").getLeft()).isInstanceOf(VerifyEmailError.InvalidToken.class);
        verifyNoInteractions(tokenRepo, userRepo);
    }

    @Test
    @DisplayName("Should reject unknown tokens")
    void shouldRejectUnknownToken() {
        when(tokenRepo.findByToken("nope")).thenReturn(Optional.empty());

        var result = useCase.execute("nope");

        assertThat(result.getLeft()).isInstanceOf(VerifyEmailError.InvalidToken.class);
        verifyNoInteractions(userRepo);
    }

    @Test
    @DisplayName("Should reject already-used tokens")
    void shouldRejectUsedToken() {
        EmailVerificationToken token = liveToken().markAsUsed(TestClock.NOW);
        when(tokenRepo.findByToken(token.getToken())).thenReturn(Optional.of(token));

        var result = useCase.execute(token.getToken());

        assertThat(result.getLeft()).isInstanceOf(VerifyEmailError.TokenAlreadyUsed.class);
        verify(userRepo, never()).markEmailVerified(any(), any());
    }

    @Test
    @DisplayName("Should reject expired tokens")
    void shouldRejectExpiredToken() {
        Instant past = TestClock.NOW.minus(3, ChronoUnit.DAYS);
        EmailVerificationToken token = EmailVerificationToken.builder()
                .token(UUID.randomUUID().toString())
                .userId(userId)
                .createdAt(past)
                .expiresAt(past.plus(48, ChronoUnit.HOURS))
                .used(false)
                .usedAt(null)
                .build();
        when(tokenRepo.findByToken(token.getToken())).thenReturn(Optional.of(token));

        var result = useCase.execute(token.getToken());

        assertThat(result.getLeft()).isInstanceOf(VerifyEmailError.TokenExpired.class);
        verify(userRepo, never()).markEmailVerified(any(), any());
    }

    @Test
    @DisplayName("Should reject when the token's user no longer exists")
    void shouldRejectWhenUserMissing() {
        EmailVerificationToken token = liveToken();
        when(tokenRepo.findByToken(token.getToken())).thenReturn(Optional.of(token));
        when(userRepo.findById(userId)).thenReturn(Optional.empty());

        var result = useCase.execute(token.getToken());

        assertThat(result.getLeft()).isInstanceOf(VerifyEmailError.InvalidToken.class);
        verify(userRepo, never()).markEmailVerified(any(), any());
        verify(tokenRepo, never()).update(any());
    }

    @Test
    @DisplayName("Should mark token used and user verified on success")
    void shouldVerifySuccessfully() {
        EmailVerificationToken token = liveToken();
        when(tokenRepo.findByToken(token.getToken())).thenReturn(Optional.of(token));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        var result = useCase.execute(token.getToken());

        assertThat(result.isRight()).isTrue();
        assertThat(result.get()).isEqualTo(new VerifyEmailResult.Success(userId, "player@example.com"));

        ArgumentCaptor<EmailVerificationToken> tokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepo).update(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().isUsed()).isTrue();
        assertThat(tokenCaptor.getValue().getUsedAt()).isNotNull();

        // The exact instant, not any(OffsetDateTime.class): the clock is read once for the token and
        // the user, so a second read would show here.
        verify(userRepo).markEmailVerified(userId, OffsetDateTime.ofInstant(TestClock.NOW, ZoneOffset.UTC));
        assertThat(tokenCaptor.getValue().getUsedAt()).isEqualTo(TestClock.NOW);
    }

    @Test
    @DisplayName("Should treat a token expiring at this exact instant as still live")
    void shouldTreatExactExpiryAsNotYetExpired() {
        // `isExpired` is strict (`at.isAfter`): a token expiring exactly now is valid and one second
        // later is not, pinning the strictness as chosen.
        EmailVerificationToken atTheBoundary = tokenExpiringAt(TestClock.NOW);
        when(tokenRepo.findByToken(atTheBoundary.getToken())).thenReturn(Optional.of(atTheBoundary));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        assertThat(useCase.execute(atTheBoundary.getToken()).isRight()).isTrue();

        EmailVerificationToken aSecondStale = tokenExpiringAt(TestClock.NOW.minusSeconds(1));
        when(tokenRepo.findByToken(aSecondStale.getToken())).thenReturn(Optional.of(aSecondStale));

        assertThat(useCase.execute(aSecondStale.getToken()).getLeft())
                .isInstanceOf(VerifyEmailError.TokenExpired.class);
    }

    private EmailVerificationToken liveToken() {
        return EmailVerificationToken.create(userId, 48, TestClock.NOW);
    }

    private EmailVerificationToken tokenExpiringAt(Instant expiresAt) {
        return EmailVerificationToken.builder()
                .token(UUID.randomUUID().toString())
                .userId(userId)
                .createdAt(expiresAt.minus(48, ChronoUnit.HOURS))
                .expiresAt(expiresAt)
                .used(false)
                .usedAt(null)
                .build();
    }
}
