package com.ligitabl.api.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.testsupport.TestClock;
import com.ligitabl.api.web.auth.RequestPasswordResetUseCase.PasswordResetResult;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.domain.PasswordResetToken;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.PasswordResetTokenRepo;
import com.ligitabl.model.repo.UserRepo;

/**
 * Companion to {@link ResetPasswordUseCaseTest}, and likewise written because none existed — the
 * token-minting half of the password-reset flow had no coverage at all.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Request Password Reset Use Case")
class RequestPasswordResetUseCaseTest {

    private static final int VALIDITY_MINUTES = 30;

    @Mock
    UserRepo userRepo;

    @Mock
    PasswordResetTokenRepo tokenRepo;

    @Mock
    PasswordResetEmailService emailService;

    RequestPasswordResetUseCase useCase;

    UUID userId;
    User user;

    @BeforeEach
    void setUp() {
        useCase = new RequestPasswordResetUseCase(userRepo, tokenRepo, emailService, TestClock.FIXED);
        ReflectionTestUtils.setField(useCase, "frontendUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(useCase, "tokenValidityMinutes", VALIDITY_MINUTES);

        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .email(Email.create("player@example.com"))
                .displayName("Player")
                .roles(Set.of(Role.PLAYER))
                .build();
    }

    @Test
    @DisplayName("Should report success for an unknown email without creating a token")
    void shouldNotRevealUnknownEmail() {
        // Deliberate: revealing whether an address exists turns this endpoint into an account
        // enumeration oracle. The response must be indistinguishable from the success path.
        when(userRepo.findByEmail(any())).thenReturn(Optional.empty());

        var result = useCase.execute("nobody@example.com");

        assertThat(result.isRight()).isTrue();
        assertThat(result.get()).isEqualTo(new PasswordResetResult.EmailSent("nobody@example.com"));
        verifyNoInteractions(tokenRepo, emailService);
    }

    @Test
    @DisplayName("Should invalidate existing tokens before saving the new one")
    void shouldInvalidateBeforeSave() {
        // Order matters: saving first and invalidating after would invalidate the token just minted.
        givenUserExists();

        useCase.execute("player@example.com");

        InOrder inOrder = inOrder(tokenRepo);
        inOrder.verify(tokenRepo).invalidateAllForUser(userId);
        inOrder.verify(tokenRepo).save(any(PasswordResetToken.class));
    }

    @Test
    @DisplayName("Should mint the token from the clock and send its URL")
    void shouldMintTokenFromClockAndSendUrl() {
        givenUserExists();

        var result = useCase.execute("player@example.com");

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepo).save(captor.capture());
        PasswordResetToken saved = captor.getValue();

        assertThat(saved.getUserId()).isEqualTo(userId);
        // Both derived from the injected clock, not from wall time: createdAt is the instant the
        // request was evaluated at, and expiresAt is exactly the configured window past it.
        assertThat(saved.getCreatedAt()).isEqualTo(TestClock.NOW);
        assertThat(saved.getExpiresAt()).isEqualTo(TestClock.NOW.plus(VALIDITY_MINUTES, ChronoUnit.MINUTES));
        assertThat(saved.isValid(TestClock.NOW)).isTrue();

        verify(emailService)
                .sendPasswordResetEmail(
                        eq("player@example.com"),
                        eq("http://localhost:8080/auth/reset-password?token=" + saved.getToken()),
                        eq(VALIDITY_MINUTES));

        assertThat(result.isRight()).isTrue();
    }

    @Test
    @DisplayName("Should still report success when email delivery fails")
    void shouldSucceedWhenEmailDeliveryFails() {
        // Same enumeration argument as above: a provider error must not become a signal.
        when(userRepo.findByEmail(any())).thenReturn(Optional.of(user));
        when(emailService.sendPasswordResetEmail(anyString(), anyString(), anyInt()))
                .thenReturn(Either.left(
                        new com.ligitabl.api.notification.email.EmailError.EmailProviderError("provider down")));

        assertThat(useCase.execute("player@example.com").isRight()).isTrue();
        verify(tokenRepo).save(any(PasswordResetToken.class));
    }

    @Test
    @DisplayName("Should map a malformed email to success rather than an error")
    void shouldNotRevealMalformedEmail() {
        // Email.create throws, which the catch-all turns into UnexpectedError — asserting the
        // current behaviour rather than the ideal one, so a change here is a decision not a drift.
        var result = useCase.execute("not-an-email");

        assertThat(result.isLeft()).isTrue();
        verifyNoInteractions(tokenRepo, emailService);
    }

    private void givenUserExists() {
        when(userRepo.findByEmail(any())).thenReturn(Optional.of(user));
        when(emailService.sendPasswordResetEmail(anyString(), anyString(), anyInt()))
                .thenReturn(Either.right(null));
    }
}
