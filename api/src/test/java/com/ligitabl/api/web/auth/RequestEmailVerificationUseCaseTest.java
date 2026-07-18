package com.ligitabl.api.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.web.auth.RequestEmailVerificationUseCase.RequestError;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.domain.EmailVerificationToken;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.EmailVerificationTokenRepo;
import com.ligitabl.model.repo.UserRepo;

@ExtendWith(MockitoExtension.class)
@DisplayName("Request Email Verification Use Case")
class RequestEmailVerificationUseCaseTest {

    @Mock
    UserRepo userRepo;

    @Mock
    EmailVerificationTokenRepo tokenRepo;

    @Mock
    EmailVerificationEmailService emailService;

    RequestEmailVerificationUseCase useCase;

    UUID userId;
    User user;

    @BeforeEach
    void setUp() {
        useCase = new RequestEmailVerificationUseCase(userRepo, tokenRepo, emailService);
        ReflectionTestUtils.setField(useCase, "frontendUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(useCase, "tokenValidityHours", 48);
        ReflectionTestUtils.setField(useCase, "resendCooldownMinutes", 2);

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
    @DisplayName("Should fail when user does not exist")
    void shouldFailWhenUserNotFound() {
        when(userRepo.findById(userId)).thenReturn(Optional.empty());

        var result = useCase.execute(userId);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RequestError.UserNotFound.class);
        verifyNoInteractions(tokenRepo, emailService);
    }

    @Test
    @DisplayName("Should short-circuit when email is already verified")
    void shouldFailWhenAlreadyVerified() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user.withEmailVerified(true)));

        var result = useCase.execute(userId);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RequestError.AlreadyVerified.class);
        verifyNoInteractions(tokenRepo, emailService);
    }

    @Test
    @DisplayName("Should throttle resend inside the cooldown window")
    void shouldThrottleResendInsideCooldown() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(tokenRepo.findLatestForUser(userId))
                .thenReturn(Optional.of(tokenCreatedAt(Instant.now().minus(30, ChronoUnit.SECONDS))));

        var result = useCase.execute(userId);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RequestError.ResendTooSoon.class);
        verify(tokenRepo, never()).invalidateAllForUser(any());
        verify(tokenRepo, never()).save(any());
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("Should allow resend once the cooldown has elapsed")
    void shouldAllowResendAfterCooldown() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(tokenRepo.findLatestForUser(userId))
                .thenReturn(Optional.of(tokenCreatedAt(Instant.now().minus(3, ChronoUnit.MINUTES))));
        when(emailService.sendVerificationEmail(anyString(), anyString(), anyInt()))
                .thenReturn(Either.right(null));

        var result = useCase.execute(userId);

        assertThat(result.isRight()).isTrue();
        verify(tokenRepo).save(any(EmailVerificationToken.class));
    }

    @Test
    @DisplayName("Should invalidate old tokens before saving the new one")
    void shouldInvalidateBeforeSave() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(tokenRepo.findLatestForUser(userId)).thenReturn(Optional.empty());
        when(emailService.sendVerificationEmail(anyString(), anyString(), anyInt()))
                .thenReturn(Either.right(null));

        useCase.execute(userId);

        InOrder inOrder = inOrder(tokenRepo);
        inOrder.verify(tokenRepo).invalidateAllForUser(userId);
        inOrder.verify(tokenRepo).save(any(EmailVerificationToken.class));
    }

    @Test
    @DisplayName("Should build the verification URL from the saved token and send it")
    void shouldSendVerificationUrlWithSavedToken() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(tokenRepo.findLatestForUser(userId)).thenReturn(Optional.empty());
        when(emailService.sendVerificationEmail(anyString(), anyString(), anyInt()))
                .thenReturn(Either.right(null));

        var result = useCase.execute(userId);

        ArgumentCaptor<EmailVerificationToken> tokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepo).save(tokenCaptor.capture());
        EmailVerificationToken saved = tokenCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.isValid()).isTrue();

        verify(emailService)
                .sendVerificationEmail(
                        eq("player@example.com"),
                        eq("http://localhost:8080/auth/verify-email?token=" + saved.getToken()),
                        eq(48));

        assertThat(result.isRight()).isTrue();
        assertThat(result.get())
                .isEqualTo(new RequestEmailVerificationUseCase.RequestResult.EmailSent("player@example.com"));
    }

    @Test
    @DisplayName("Should still return success when email delivery fails")
    void shouldSucceedWhenEmailDeliveryFails() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(tokenRepo.findLatestForUser(userId)).thenReturn(Optional.empty());
        when(emailService.sendVerificationEmail(anyString(), anyString(), anyInt()))
                .thenReturn(Either.left(
                        new com.ligitabl.api.notification.email.EmailError.EmailProviderError("provider down")));

        var result = useCase.execute(userId);

        assertThat(result.isRight()).isTrue();
        verify(tokenRepo).save(any(EmailVerificationToken.class));
    }

    @Test
    @DisplayName("Should map unexpected exceptions to UnexpectedError")
    void shouldMapUnexpectedExceptions() {
        when(userRepo.findById(userId)).thenThrow(new RuntimeException("boom"));

        var result = useCase.execute(userId);

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(RequestError.UnexpectedError.class);
    }

    private static EmailVerificationToken tokenCreatedAt(Instant createdAt) {
        return EmailVerificationToken.builder()
                .token(UUID.randomUUID().toString())
                .userId(UUID.randomUUID())
                .createdAt(createdAt)
                .expiresAt(createdAt.plus(48, ChronoUnit.HOURS))
                .used(false)
                .usedAt(null)
                .build();
    }
}
