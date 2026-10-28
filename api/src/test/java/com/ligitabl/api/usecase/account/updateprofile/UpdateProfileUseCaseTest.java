package com.ligitabl.api.usecase.account.updateprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.usecase.account.AccountTestUsers;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

@ExtendWith(MockitoExtension.class)
class UpdateProfileUseCaseTest {

    @Mock
    UserRepo userRepo;

    @InjectMocks
    UpdateProfileUseCase useCase;

    @Test
    void updatesDisplayNameAndStoresOptOutInverted() {
        User user = AccountTestUsers.user(null, AccountTestUsers.HASHED);
        when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));

        var result = useCase.execute(user.getId(), "Alice B", false);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepo).update(saved.capture());
        assertThat(saved.getValue().getDisplayName()).isEqualTo("Alice B");
        assertThat(saved.getValue().isResultsEmailOptOut()).isTrue();
        assertThat(result.get()).isEqualTo(saved.getValue());
    }

    @Test
    void userNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepo.findById(userId)).thenReturn(Optional.empty());

        var result = useCase.execute(userId, "Alice", true);

        assertThat(result.getLeft()).isEqualTo(new UpdateProfileError.UserNotFound(userId));
        verify(userRepo, never()).update(any());
    }
}
