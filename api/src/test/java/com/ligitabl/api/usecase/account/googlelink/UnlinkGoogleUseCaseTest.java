package com.ligitabl.api.usecase.account.googlelink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.usecase.account.AccountTestUsers;
import com.ligitabl.model.domain.identity.User;
import com.ligitabl.model.repo.UserRepo;

@ExtendWith(MockitoExtension.class)
class UnlinkGoogleUseCaseTest {

    @Mock
    UserRepo userRepo;

    @InjectMocks
    UnlinkGoogleUseCase useCase;

    @Test
    void linkedWithPassword_unlinks() {
        User user = AccountTestUsers.user("google-sub", AccountTestUsers.HASHED);
        when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));

        var result = useCase.execute(user.getId());

        assertThat(result.get().getGoogleId()).isNull();
        verify(userRepo).unlinkGoogle(user.getId());
    }

    @Test
    void noPassword_rejectedSoTheAccountKeepsASignIn() {
        User user = AccountTestUsers.user("google-sub", null);
        when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));

        var result = useCase.execute(user.getId());

        assertThat(result.getLeft()).isEqualTo(new UnlinkGoogleError.PasswordRequired(user.getId()));
        verify(userRepo, never()).unlinkGoogle(any());
    }

    @Test
    void notLinked_rejected() {
        User user = AccountTestUsers.user(null, AccountTestUsers.HASHED);
        when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));

        var result = useCase.execute(user.getId());

        assertThat(result.getLeft()).isEqualTo(new UnlinkGoogleError.NotLinked(user.getId()));
        verify(userRepo, never()).unlinkGoogle(any());
    }

    @Test
    void userNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepo.findById(userId)).thenReturn(Optional.empty());

        var result = useCase.execute(userId);

        assertThat(result.getLeft()).isEqualTo(new UnlinkGoogleError.UserNotFound(userId));
    }
}
