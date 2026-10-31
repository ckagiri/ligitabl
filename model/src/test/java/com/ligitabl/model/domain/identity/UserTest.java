package com.ligitabl.model.domain.identity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserTest {

    private User testUser;
    private UUID userId;
    private PublicId publicId;
    private Email email;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        publicId = PublicId.create("AbCd3fGh9J");
        email = Email.create("test@example.com");

        testUser = User.builder()
                .id(userId)
                .publicId(publicId)
                .email(email)
                .displayName("Test User")
                .password(Password.Hashed.of("$2a$10$hashedPassword"))
                .roles(Set.of(Role.PLAYER))
                .emailVerified(false)
                .build();
    }

    @Test
    void shouldCreateUserWithBuilder() {
        assertNotNull(testUser);
        assertEquals(userId, testUser.getId());
        assertEquals(publicId, testUser.getPublicId());
        assertEquals(email, testUser.getEmail());
        assertEquals("Test User", testUser.getDisplayName());
        assertFalse(testUser.isEmailVerified());
        assertEquals(1, testUser.getRoles().size());
        assertTrue(testUser.getRoles().contains(Role.PLAYER));
    }

    @Test
    void shouldCheckIfUserHasRole() {
        assertTrue(testUser.hasRole(Role.PLAYER));
        assertFalse(testUser.hasRole(Role.ADMIN));
    }

    @Test
    void shouldCheckIfUserHasAnyRole() {
        assertTrue(testUser.hasAnyRole(Role.PLAYER, Role.ADMIN));
        assertTrue(testUser.hasAnyRole(Role.PLAYER));
        assertFalse(testUser.hasAnyRole(Role.ADMIN, Role.MODERATOR));
    }

    @Test
    void shouldCheckIfUserHasAllRoles() {
        User multiRoleUser = testUser.withRoles(Set.of(Role.ADMIN, Role.PLAYER));

        assertTrue(multiRoleUser.hasAllRoles(Role.ADMIN, Role.PLAYER));
        assertFalse(multiRoleUser.hasAllRoles(Role.ADMIN, Role.PLAYER, Role.MODERATOR));
        assertFalse(testUser.hasAllRoles(Role.ADMIN, Role.PLAYER));
    }

    @Test
    void shouldAddRoleImmutably() {
        User updatedUser = testUser.addRole(Role.ADMIN);

        assertEquals(1, testUser.getRoles().size());
        assertFalse(testUser.hasRole(Role.ADMIN));

        assertEquals(2, updatedUser.getRoles().size());
        assertTrue(updatedUser.hasRole(Role.PLAYER));
        assertTrue(updatedUser.hasRole(Role.ADMIN));
    }

    @Test
    void shouldRemoveRoleImmutably() {
        User multiRoleUser = testUser.withRoles(Set.of(Role.ADMIN, Role.PLAYER));

        User updatedUser = multiRoleUser.removeRole(Role.ADMIN);

        assertEquals(2, multiRoleUser.getRoles().size());

        assertEquals(1, updatedUser.getRoles().size());
        assertTrue(updatedUser.hasRole(Role.PLAYER));
        assertFalse(updatedUser.hasRole(Role.ADMIN));
    }

    @Test
    void shouldUpdateDisplayNameImmutably() {
        User updatedUser = testUser.withDisplayName("New Name");

        assertEquals("Test User", testUser.getDisplayName());
        assertEquals("New Name", updatedUser.getDisplayName());

        assertEquals(testUser.getId(), updatedUser.getId());
        assertEquals(testUser.getPublicId(), updatedUser.getPublicId());
        assertEquals(testUser.getEmail(), updatedUser.getEmail());
    }

    @Test
    void shouldUpdateEmailVerificationImmutably() {
        User verifiedUser = testUser.withEmailVerified(true);

        assertFalse(testUser.isEmailVerified());
        assertTrue(verifiedUser.isEmailVerified());
    }

    @Test
    void shouldUpdatePasswordImmutably() {
        Password.Hashed newPassword = Password.Hashed.of("$2a$10$newHashedPassword");

        User updatedUser = testUser.withPassword(newPassword);

        assertNotEquals(testUser.getPassword(), updatedUser.getPassword());
        assertEquals(newPassword, updatedUser.getPassword());
    }

    @Test
    void googleLinkedWithPassword_canUnlink() {
        User user = testUser.withGoogleId("g-sub");

        assertTrue(user.hasPassword());
        assertTrue(user.isGoogleLinked());
        assertTrue(user.canUnlinkGoogle());
    }

    @Test
    void googleOnly_cannotUnlink() {
        User user = testUser.withGoogleId("g-sub").withPassword(null);

        assertFalse(user.hasPassword());
        assertTrue(user.isGoogleLinked());
        assertFalse(user.canUnlinkGoogle());
    }

    @Test
    void blankGoogleId_isNotLinked() {
        assertFalse(testUser.isGoogleLinked());
        assertFalse(testUser.withGoogleId(" ").isGoogleLinked());
        assertFalse(testUser.withGoogleId(" ").canUnlinkGoogle());
    }

    @Test
    void sameIdIsTheSameUserWhateverTheOtherFields() {
        User renamed = testUser.withDisplayName("New Name");

        assertEquals(testUser, renamed);
        assertEquals(testUser.hashCode(), renamed.hashCode());
    }

    @Test
    void unsavedUserEqualsOnlyItself() {
        User unsaved = User.builder().publicId(publicId).email(email).build();

        assertEquals(unsaved, unsaved);
        assertNotEquals(unsaved, User.builder().publicId(publicId).email(email).build());
    }

    @Test
    void rolesSetShouldBeImmutableWhenUsingSetOf() {
        Set<Role> originalRoles = testUser.getRoles();
        assertThrows(UnsupportedOperationException.class, () -> originalRoles.add(Role.ADMIN));
        assertEquals(1, testUser.getRoles().size());
        assertFalse(testUser.hasRole(Role.ADMIN));
    }
}
