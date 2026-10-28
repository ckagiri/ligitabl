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
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

@ExtendWith(MockitoExtension.class)
class LinkGoogleUseCaseTest {

    @Mock
    UserRepo userRepo;

    @InjectMocks
    LinkGoogleUseCase useCase;

    @Test
    void unclaimedSubject_linksAndVerifiesEmail() {
        User user = AccountTestUsers.user(null, AccountTestUsers.HASHED);
        when(userRepo.findById(user.getId())).thenReturn(Optional.of(user));
        when(userRepo.findByGoogleId("g-sub")).thenReturn(Optional.empty());

        var result = useCase.execute(user.getId(), "g-sub");

        assertThat(result.get().getGoogleId()).isEqualTo("g-sub");
        assertThat(result.get().isEmailVerified()).isTrue();
        verify(userRepo).update(result.get());
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
        verify(userRepo, never()).update(any());
    }

    @Test
    void userNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepo.findById(userId)).thenReturn(Optional.empty());

        assertThat(useCase.execute(userId, "g-sub").getLeft()).isEqualTo(new LinkGoogleError.UserNotFound(userId));
    }
}
