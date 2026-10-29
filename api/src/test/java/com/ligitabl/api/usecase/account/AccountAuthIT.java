package com.ligitabl.api.usecase.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ligitabl.api.testsupport.AbstractPostgresIT;
import com.ligitabl.api.usecase.account.googlelink.UnlinkGoogleError;
import com.ligitabl.api.usecase.account.googlelink.UnlinkGoogleUseCase;
import com.ligitabl.api.usecase.account.setpassword.SetPasswordUseCase;
import com.ligitabl.api.usecase.account.updateprofile.UpdateProfileUseCase;
import com.ligitabl.api.usecase.auth.googlesignin.GoogleIdentity;
import com.ligitabl.api.usecase.auth.googlesignin.SignInWithGoogleError;
import com.ligitabl.api.usecase.auth.googlesignin.SignInWithGoogleUseCase;
import com.ligitabl.api.usecase.auth.login.VerifyCredentialsUseCase;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.Password;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.domain.service.PasswordHasher;
import com.ligitabl.model.domain.service.PublicIdGenerator;
import com.ligitabl.model.repo.UserRepo;

/**
 * Account and sign-in flows against real Postgres. The unit tests mock {@code UserRepo}, so they
 * can't see a write that skips a column; set-password once returned success without saving.
 * Each test reloads the row and signs in for real.
 *
 * <p>Emails and Google subjects are random per test, so the shared container needs no cleanup.
 */
@SpringBootTest
@DisplayName("Account and sign-in flows (real Postgres)")
class AccountAuthIT extends AbstractPostgresIT {

    private static final String PASSWORD = "password123";

    @Autowired
    UserRepo userRepo;

    @Autowired
    PasswordHasher passwordHasher;

    @Autowired
    PublicIdGenerator publicIdGenerator;

    @Autowired
    SignInWithGoogleUseCase signInWithGoogle;

    @Autowired
    SetPasswordUseCase setPassword;

    @Autowired
    UnlinkGoogleUseCase unlinkGoogle;

    @Autowired
    UpdateProfileUseCase updateProfile;

    @Autowired
    VerifyCredentialsUseCase verifyCredentials;

    @Test
    void setPassword_thenSignInWithIt() {
        User user = signInWithGoogle.execute(googleIdentity(randomEmail())).get();

        assertThat(setPassword.execute(user.getId(), PASSWORD).isRight()).isTrue();

        assertThat(reload(user).getPassword()).isNotNull();
        assertThat(signsIn(user, PASSWORD)).isTrue();
    }

    @Test
    void googleSignIn_newEmail_createsVerifiedGoogleOnlyAccount() {
        String email = randomEmail();
        var identity = googleIdentity(email);

        User created = signInWithGoogle.execute(identity).get();

        User stored = reload(created);
        assertThat(stored.getEmail().value()).isEqualTo(email);
        assertThat(stored.getGoogleId()).isEqualTo(identity.subject());
        assertThat(stored.getPassword()).isNull();
        assertThat(stored.isEmailVerified()).isTrue();
        assertThat(stored.getEmailVerifiedAt()).isNotNull();
        assertThat(signsIn(stored, PASSWORD)).isFalse();

        assertThat(signInWithGoogle.execute(identity).get().getId()).isEqualTo(created.getId());
    }

    @Test
    void googleSignIn_existingPasswordAccount_linksAndKeepsPassword() {
        User existing = createPasswordUser(randomEmail());
        var identity = googleIdentity(existing.getEmail().value());

        assertThat(signInWithGoogle.execute(identity).get().getId()).isEqualTo(existing.getId());

        User stored = reload(existing);
        assertThat(stored.getGoogleId()).isEqualTo(identity.subject());
        assertThat(stored.isEmailVerified()).isTrue();
        assertThat(stored.getEmailVerifiedAt()).isNotNull();
        assertThat(signsIn(stored, PASSWORD)).isTrue();
    }

    @Test
    void googleSignIn_emailLinkedToAnotherGoogleAccount_rejectedAndRowUnchanged() {
        User existing = signInWithGoogle.execute(googleIdentity(randomEmail())).get();

        var result = signInWithGoogle.execute(googleIdentity(existing.getEmail().value()));

        assertThat(result.getLeft()).isInstanceOf(SignInWithGoogleError.EmailLinkedToOtherGoogleAccount.class);
        assertThat(reload(existing).getGoogleId()).isEqualTo(existing.getGoogleId());
    }

    @Test
    void unlink_refusedWithoutPassword_allowedOnceOneIsSet() {
        User user = signInWithGoogle.execute(googleIdentity(randomEmail())).get();

        assertThat(unlinkGoogle.execute(user.getId()).getLeft())
                .isEqualTo(new UnlinkGoogleError.PasswordRequired(user.getId()));
        assertThat(reload(user).getGoogleId()).isEqualTo(user.getGoogleId());

        setPassword.execute(user.getId(), PASSWORD);
        assertThat(unlinkGoogle.execute(user.getId()).isRight()).isTrue();

        User stored = reload(user);
        assertThat(stored.getGoogleId()).isNull();
        assertThat(signsIn(stored, PASSWORD)).isTrue();
    }

    @Test
    void updateProfile_writesOnlyProfileColumns() {
        User user = signInWithGoogle.execute(googleIdentity(randomEmail())).get();
        setPassword.execute(user.getId(), PASSWORD);

        updateProfile.execute(user.getId(), "Renamed", false);

        User stored = reload(user);
        assertThat(stored.getDisplayName()).isEqualTo("Renamed");
        assertThat(stored.isResultsEmailOptOut()).isTrue();
        assertThat(stored.getGoogleId()).isEqualTo(user.getGoogleId());
        assertThat(stored.isEmailVerified()).isTrue();
        assertThat(signsIn(stored, PASSWORD)).isTrue();
    }

    @Test
    void linkGoogle_keepsAnEarlierVerificationTime() {
        User user = createPasswordUser(randomEmail());
        OffsetDateTime verifiedEarlier =
                OffsetDateTime.now(ZoneOffset.UTC).minusDays(3).truncatedTo(ChronoUnit.SECONDS);
        userRepo.markEmailVerified(user.getId(), verifiedEarlier);

        userRepo.linkGoogle(user.getId(), "g-" + UUID.randomUUID(), OffsetDateTime.now(ZoneOffset.UTC));

        assertThat(reload(user).getEmailVerifiedAt()).isAtSameInstantAs(verifiedEarlier);
    }

    private User createPasswordUser(String email) {
        UUID id = UUID.randomUUID();
        return userRepo.create(User.builder()
                .id(id)
                .publicId(publicIdGenerator.generate(id))
                .email(Email.create(email))
                .displayName("Alice")
                .password(passwordHasher.hash(Password.Plaintext.create(PASSWORD)))
                .roles(Set.of(Role.PLAYER))
                .build());
    }

    private boolean signsIn(User user, String password) {
        return verifyCredentials
                .execute(user.getEmail(), Password.Plaintext.create(password))
                .isRight();
    }

    private User reload(User user) {
        return userRepo.findById(user.getId()).orElseThrow();
    }

    private static GoogleIdentity googleIdentity(String email) {
        return new GoogleIdentity("g-" + UUID.randomUUID(), email, "Alice");
    }

    private static String randomEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }
}
