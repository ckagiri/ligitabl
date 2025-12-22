package com.ligitabl.api.usecases.auth.getcurrentuser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.NotFoundError;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.Password;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

@ExtendWith(MockitoExtension.class)
class GetCurrentUserUseCaseTest {

    @Mock
    UserRepo userRepo;

    GetCurrentUserUseCase getCurrentUserUseCase;

    PublicId publicId;
    User user;

    @BeforeEach
    void setUp() {
        getCurrentUserUseCase = new GetCurrentUserHandler(userRepo);
        publicId = PublicId.create("AbCd3fGh9J");

        user = User.builder()
                .id(UUID.randomUUID())
                .publicId(publicId)
                .email(Email.create("test@example.com"))
                .displayName("Test User")
                .password(Password.Hashed.of("$2a$10$hash"))
                .roles(Set.of(Role.PLAYER, Role.ADMIN))
                .emailVerified(true)
                .build();
    }

    @Test
    void shouldReturnUserInfoWhenUserExists() {
        when(userRepo.findByPublicId(publicId)).thenReturn(Optional.of(user));

        Either<UseCaseError, UserInfo> result = getCurrentUserUseCase.execute(new GetCurrentUserQuery(publicId));

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().publicId()).isEqualTo(publicId);
        assertThat(result.get().email()).isEqualTo(user.getEmail());
        assertThat(result.get().displayName()).isEqualTo("Test User");
        assertThat(result.get().roles()).containsExactlyInAnyOrder(Role.PLAYER, Role.ADMIN);
        assertThat(result.get().emailVerified()).isTrue();
    }

    @Test
    void shouldFailWhenUserNotFound() {
        when(userRepo.findByPublicId(publicId)).thenReturn(Optional.empty());

        Either<UseCaseError, UserInfo> result = getCurrentUserUseCase.execute(new GetCurrentUserQuery(publicId));

        assertThat(result.isLeft()).isTrue();
        assertThat(result.getLeft()).isInstanceOf(NotFoundError.class);
    }

    @Test
    void shouldIncludeUnverifiedStatus() {
        User unverified = user.withEmailVerified(false);
        when(userRepo.findByPublicId(publicId)).thenReturn(Optional.of(unverified));

        Either<UseCaseError, UserInfo> result = getCurrentUserUseCase.execute(new GetCurrentUserQuery(publicId));

        assertThat(result.isRight()).isTrue();
        assertThat(result.get().emailVerified()).isFalse();
    }
}
