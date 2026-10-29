package com.ligitabl.api.usecase.account.setpassword;

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
import com.ligitabl.model.auth.Password;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.domain.service.PasswordHasher;
import com.ligitabl.model.repo.UserRepo;

@ExtendWith(MockitoExtension.class)
class SetPasswordUseCaseTest {

    @Mock
    UserRepo userRepo;

    @Mock
    PasswordHasher passwordHasher;

    @InjectMocks
    SetPasswordUseCase useCase;

    @Test
    void googleOnlyUser_getsHashedPassword() {
        User user = AccountTestUsers.user("google-sub", null);
        Password.Hashed hashed = Password.Hashed.of("$2a$10$newHash");
        when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordHasher.hash(any())).thenReturn(hashed);

        var result = useCase.execute(user.getId(), "password123");

        assertThat(result.get().getPassword()).isEqualTo(hashed);
        verify(userRepo).updatePassword(user.getId(), hashed);
    }

    @Test
    void passwordAlreadySet_rejected() {
        User user = AccountTestUsers.user(null, AccountTestUsers.HASHED);
        when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));

        var result = useCase.execute(user.getId(), "password123");

        assertThat(result.getLeft()).isEqualTo(new SetPasswordError.AlreadySet(user.getId()));
        verify(userRepo, never()).updatePassword(any(), any());
    }

    @Test
    void tooShort_invalidPassword() {
        User user = AccountTestUsers.user("google-sub", null);
        when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));

        var result = useCase.execute(user.getId(), "short");

        assertThat(result.getLeft()).isInstanceOf(SetPasswordError.InvalidPassword.class);
        verifyNoInteractions(passwordHasher);
        verify(userRepo, never()).updatePassword(any(), any());
    }

    @Test
    void userNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepo.findById(userId)).thenReturn(Optional.empty());

        var result = useCase.execute(userId, "password123");

        assertThat(result.getLeft()).isEqualTo(new SetPasswordError.UserNotFound(userId));
    }
}
