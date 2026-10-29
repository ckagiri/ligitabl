package com.ligitabl.api.usecase.auth.googlesignin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.testsupport.TestClock;
import com.ligitabl.api.usecase.account.AccountTestUsers;
import com.ligitabl.api.usecase.shared.AdminAlerts;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.domain.service.PublicIdGenerator;
import com.ligitabl.model.repo.UserRepo;

@ExtendWith(MockitoExtension.class)
class SignInWithGoogleUseCaseTest {

    private static final Email ALICE = Email.create("alice@example.com");

    @Mock
    UserRepo userRepo;

    @Mock
    PublicIdGenerator publicIdGenerator;

    @Mock
    AdminAlerts adminAlerts;

    private SignInWithGoogleUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new SignInWithGoogleUseCase(userRepo, publicIdGenerator, adminAlerts, TestClock.FIXED);
    }

    @Test
    void alreadyLinked_returnsThatUserUnchanged() {
        User linked = AccountTestUsers.user("g-sub", null);
        when(userRepo.findByGoogleId("g-sub")).thenReturn(Optional.of(linked));

        var result = useCase.execute(new GoogleIdentity("g-sub", "alice@example.com", "Alice"));

        assertThat(result.get()).isEqualTo(linked);
        verify(userRepo, never()).linkGoogle(any(), any(), any());
        verify(userRepo, never()).create(any());
    }

    @Test
    void sameEmailNotYetLinked_linksAndVerifies() {
        User existing = AccountTestUsers.user(null, AccountTestUsers.HASHED);
        when(userRepo.findByGoogleId("g-sub")).thenReturn(Optional.empty());
        when(userRepo.findByEmail(ALICE)).thenReturn(Optional.of(existing));

        var result = useCase.execute(new GoogleIdentity("g-sub", " Alice@Example.com ", "Alice"));

        User linked = result.get();
        assertThat(linked.getGoogleId()).isEqualTo("g-sub");
        assertThat(linked.isEmailVerified()).isTrue();
        assertThat(linked.getEmailVerifiedAt()).isEqualTo(TestClock.NOW.atOffset(ZoneOffset.UTC));
        verify(userRepo).linkGoogle(existing.getId(), "g-sub", TestClock.NOW.atOffset(ZoneOffset.UTC));
        verifyNoInteractions(adminAlerts);
    }

    @Test
    void sameEmailLinkedToAnotherGoogleAccount_rejected() {
        User existing = AccountTestUsers.user("other-sub", AccountTestUsers.HASHED);
        when(userRepo.findByGoogleId("g-sub")).thenReturn(Optional.empty());
        when(userRepo.findByEmail(ALICE)).thenReturn(Optional.of(existing));

        var result = useCase.execute(new GoogleIdentity("g-sub", "alice@example.com", "Alice"));

        assertThat(result.getLeft())
                .isEqualTo(new SignInWithGoogleError.EmailLinkedToOtherGoogleAccount("alice@example.com"));
        verify(userRepo, never()).linkGoogle(any(), any(), any());
    }

    @Test
    void newEmail_createsGoogleOnlyPlayerAndAlerts() {
        when(userRepo.findByGoogleId("g-sub")).thenReturn(Optional.empty());
        when(userRepo.findByEmail(any())).thenReturn(Optional.empty());
        when(publicIdGenerator.generate(any())).thenReturn(PublicId.create("abcdefghjk"));

        var result = useCase.execute(new GoogleIdentity("g-sub", "jane.doe_smith@example.com", " "));

        ArgumentCaptor<User> created = ArgumentCaptor.forClass(User.class);
        verify(userRepo).create(created.capture());
        assertThat(created.getValue().getPassword()).isNull();
        assertThat(created.getValue().getGoogleId()).isEqualTo("g-sub");
        assertThat(created.getValue().getDisplayName()).isEqualTo("Jane Doe Smith");
        assertThat(result.get()).isEqualTo(created.getValue());
        verify(adminAlerts)
                .notifyNewUserSignup("abcdefghjk", "Jane Doe Smith", "jane.doe_smith@example.com", "Google sign-in");
    }
}
