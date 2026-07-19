package com.ligitabl.api.auth.impersonation;

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

import com.ligitabl.api.auth.impersonation.ImpersonationAuthorizationService.Result;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.UserRepo;

@ExtendWith(MockitoExtension.class)
class PlayerImpersonationAuthorizationServiceTest {

    @Mock
    UserRepo userRepo;

    PlayerImpersonationAuthorizationService service;

    User admin;
    User player;

    @BeforeEach
    void setUp() {
        service = new PlayerImpersonationAuthorizationService(userRepo);
        admin = user("admin@example.com", Set.of(Role.ADMIN));
        player = user("player@example.com", Set.of(Role.PLAYER));
    }

    @Test
    void happyPathByEmail() {
        when(userRepo.findByEmail(Email.create("player@example.com"))).thenReturn(Optional.of(player));

        var result = service.assertCanImpersonate(admin, "player@example.com");

        assertThat(result).isEqualTo(new Result.Ok(player));
    }

    @Test
    void happyPathByUsername() {
        when(userRepo.findByUsername("foobar")).thenReturn(Optional.of(player));

        var result = service.assertCanImpersonate(admin, "FooBar");

        assertThat(result).isEqualTo(new Result.Ok(player));
        verify(userRepo, never()).findByEmail(any());
    }

    @Test
    void unknownUsernameIsNotFound() {
        when(userRepo.findByUsername("ghost")).thenReturn(Optional.empty());

        var result = service.assertCanImpersonate(admin, "ghost");

        assertThat(result).isEqualTo(new Result.TargetNotFound("ghost"));
    }

    @Test
    void unknownEmailIsNotFound() {
        when(userRepo.findByEmail(Email.create("ghost@example.com"))).thenReturn(Optional.empty());

        var result = service.assertCanImpersonate(admin, "ghost@example.com");

        assertThat(result).isEqualTo(new Result.TargetNotFound("ghost@example.com"));
    }

    @Test
    void malformedEmailIsNotFound() {
        var result = service.assertCanImpersonate(admin, "not [an] email@");

        assertThat(result).isEqualTo(new Result.TargetNotFound("not [an] email@"));
        verifyNoInteractions(userRepo);
    }

    @Test
    void blankIdentifierIsNotFound() {
        var result = service.assertCanImpersonate(admin, "   ");

        assertThat(result).isInstanceOf(Result.TargetNotFound.class);
    }

    @Test
    void nonAdminOriginalIsRejected() {
        var result = service.assertCanImpersonate(player, "someone@example.com");

        assertThat(result).isEqualTo(new Result.NotAdmin());
        verifyNoInteractions(userRepo);
    }

    @Test
    void nullOriginalIsRejected() {
        assertThat(service.assertCanImpersonate(null, "someone@example.com")).isEqualTo(new Result.NotAdmin());
    }

    @Test
    void selfImpersonationIsRejected() {
        when(userRepo.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));

        var result = service.assertCanImpersonate(admin, admin.getEmail().value());

        assertThat(result).isEqualTo(new Result.SelfImpersonation());
    }

    @Test
    void privilegedTargetsAreRejected() {
        for (Role role : new Role[] {Role.ADMIN, Role.MODERATOR, Role.SUPER_ADMIN}) {
            User target = user("target@example.com", Set.of(Role.PLAYER, role));
            when(userRepo.findByEmail(Email.create("target@example.com"))).thenReturn(Optional.of(target));

            var result = service.assertCanImpersonate(admin, "target@example.com");

            assertThat(result).isEqualTo(new Result.TargetPrivileged("target@example.com"));
        }
    }

    @Test
    void roleLessTargetIsAllowed() {
        User target = user("target@example.com", Set.of());
        when(userRepo.findByEmail(Email.create("target@example.com"))).thenReturn(Optional.of(target));

        var result = service.assertCanImpersonate(admin, "target@example.com");

        assertThat(result).isEqualTo(new Result.Ok(target));
    }

    private static User user(String email, Set<Role> roles) {
        return User.builder()
                .id(UUID.randomUUID())
                .publicId(PublicId.create("AbCd3fGh9J"))
                .email(Email.create(email))
                .displayName("Someone")
                .roles(roles)
                .emailVerified(true)
                .build();
    }
}
