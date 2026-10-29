package com.ligitabl.api.usecase.account.googlelink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.testsupport.TestClock;
import com.ligitabl.api.usecase.account.AccountTestUsers;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

@ExtendWith(MockitoExtension.class)
class LinkGoogleUseCaseTest {

    private static final OffsetDateTime NOW = TestClock.NOW.atOffset(ZoneOffset.UTC);

    @Mock
    UserRepo userRepo;

    LinkGoogleUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new LinkGoogleUseCase(userRepo, TestClock.FIXED);
    }

    @Test
    void unclaimedSubject_linksAndVerifiesEmail() {
        User user = AccountTestUsers.user(null, AccountTestUsers.HASHED);
        when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepo.findByGoogleId("g-sub")).thenReturn(Optional.empty());

        var result = useCase.execute(user.getId(), "g-sub");

        assertThat(result.get().getGoogleId()).isEqualTo("g-sub");
        assertThat(result.get().isEmailVerified()).isTrue();
        assertThat(result.get().getEmailVerifiedAt()).isEqualTo(NOW);
        verify(userRepo).linkGoogle(user.getId(), "g-sub", NOW);
    }

    @Test
    void alreadyVerified_keepsOriginalVerificationTime() {
        OffsetDateTime verifiedEarlier = NOW.minusDays(3);
        User user = AccountTestUsers.user(null, AccountTestUsers.HASHED)
                .withEmailVerified(true)
                .withEmailVerifiedAt(verifiedEarlier);
        when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepo.findByGoogleId("g-sub")).thenReturn(Optional.empty());

        var result = useCase.execute(user.getId(), "g-sub");

        assertThat(result.get().getEmailVerifiedAt()).isEqualTo(verifiedEarlier);
    }

    @Test
    void relinkingOwnSubject_isAllowed() {
        User user = AccountTestUsers.user("g-sub", AccountTestUsers.HASHED);
        when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepo.findByGoogleId("g-sub")).thenReturn(Optional.of(user));

        assertThat(useCase.execute(user.getId(), "g-sub").isRight()).isTrue();
    }

    @Test
    void subjectLinkedToAnotherUser_rejected() {
        User user = AccountTestUsers.user(null, AccountTestUsers.HASHED);
        User other = AccountTestUsers.user("g-sub", AccountTestUsers.HASHED);
        when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepo.findByGoogleId("g-sub")).thenReturn(Optional.of(other));

        var result = useCase.execute(user.getId(), "g-sub");

        assertThat(result.getLeft()).isEqualTo(new LinkGoogleError.LinkedToAnotherUser(other.getId()));
        verify(userRepo, never()).linkGoogle(any(), any(), any());
    }

    @Test
    void userNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepo.findById(userId)).thenReturn(Optional.empty());

        assertThat(useCase.execute(userId, "g-sub").getLeft()).isEqualTo(new LinkGoogleError.UserNotFound(userId));
    }
}
