package com.ligitabl.api.rest.admin.updateusername;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

@ExtendWith(MockitoExtension.class)
class UpdateUsernameUseCaseTest {

    @Mock
    UserRepo userRepo;

    UpdateUsernameUseCase useCase;

    UUID userId;
    User user;

    @BeforeEach
    void setUp() {
        useCase = new UpdateUsernameUseCase(userRepo);

        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .publicId(PublicId.create("AbCd3fGh9J"))
                .email(Email.create("test@example.com"))
                .displayName("Test User")
                .roles(Set.of(Role.PLAYER))
                .emailVerified(true)
                .build();
    }

    @Test
    void shouldSetUsername() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(userRepo.existsByUsername("foobar_1")).thenReturn(false);

        var result = useCase.execute(userId, "foobar_1");

        assertThat(result).isEqualTo(new UpdateUsernameUseCase.Result.Ok(userId, "foobar_1"));
        verify(userRepo).updateUsername(userId, "foobar_1");
    }

    @Test
    void shouldNormalizeToLowercaseAndTrim() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(userRepo.existsByUsername("foobar")).thenReturn(false);

        var result = useCase.execute(userId, "  FooBar  ");

        assertThat(result).isEqualTo(new UpdateUsernameUseCase.Result.Ok(userId, "foobar"));
        verify(userRepo).updateUsername(userId, "foobar");
    }

    @Test
    void shouldClearUsernameOnBlankInput() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user.withUsername("foobar")));

        var result = useCase.execute(userId, "   ");

        assertThat(result).isEqualTo(new UpdateUsernameUseCase.Result.Ok(userId, null));
        verify(userRepo).updateUsername(userId, null);
    }

    @Test
    void shouldClearUsernameOnNullInput() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user.withUsername("foobar")));

        var result = useCase.execute(userId, null);

        assertThat(result).isEqualTo(new UpdateUsernameUseCase.Result.Ok(userId, null));
        verify(userRepo).updateUsername(userId, null);
    }

    @Test
    void shouldNotUpdateWhenUnchanged() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user.withUsername("foobar")));

        var result = useCase.execute(userId, "FooBar");

        assertThat(result).isEqualTo(new UpdateUsernameUseCase.Result.Ok(userId, "foobar"));
        verify(userRepo, never()).existsByUsername(any());
        verify(userRepo, never()).updateUsername(any(), any());
    }

    @Test
    void shouldRejectInvalidFormats() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        for (String input : new String[] {"foo@bar", "fo", "foo bar", "foo-bar", "a".repeat(31)}) {
            var result = useCase.execute(userId, input);
            assertThat(result).isEqualTo(new UpdateUsernameUseCase.Result.InvalidFormat(input));
        }

        verify(userRepo, never()).updateUsername(any(), any());
    }

    @Test
    void shouldRejectTakenUsername() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(userRepo.existsByUsername("foobar")).thenReturn(true);

        var result = useCase.execute(userId, "foobar");

        assertThat(result).isEqualTo(new UpdateUsernameUseCase.Result.UsernameTaken("foobar"));
        verify(userRepo, never()).updateUsername(any(), any());
    }

    @Test
    void shouldMapUniqueConstraintRaceToTaken() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(userRepo.existsByUsername("foobar")).thenReturn(false);
        doThrow(new DuplicateKeyException("uq_t_user_username")).when(userRepo).updateUsername(userId, "foobar");

        var result = useCase.execute(userId, "foobar");

        assertThat(result).isEqualTo(new UpdateUsernameUseCase.Result.UsernameTaken("foobar"));
    }

    @Test
    void shouldReturnUserNotFound() {
        when(userRepo.findById(userId)).thenReturn(Optional.empty());

        var result = useCase.execute(userId, "foobar");

        assertThat(result).isEqualTo(new UpdateUsernameUseCase.Result.UserNotFound(userId));
        verify(userRepo, never()).updateUsername(any(), any());
    }
}
