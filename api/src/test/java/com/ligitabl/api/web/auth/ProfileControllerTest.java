package com.ligitabl.api.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.ligitabl.api.auth.impersonation.CurrentUserFacade;
import com.ligitabl.api.auth.impersonation.ImpersonationGuard;
import com.ligitabl.api.auth.security.WebUserDetails;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.web.predictions.latestresult.LatestResultSupport;
import com.ligitabl.api.web.shared.season.SeasonPredictionSupport;
import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.domain.service.PasswordHasher;
import com.ligitabl.model.repo.UserRepo;

/**
 * Focused on the round-results email opt-out checkbox (Part 6). The rest of
 * ProfileController's behavior (impersonation, password set, share data) has
 * no dedicated test coverage to extend, so this stays narrow rather than
 * attempting a full controller test from scratch.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfileControllerTest {

    @Mock
    UserRepo userRepo;

    @Mock
    PasswordHasher passwordHasher;

    @Mock
    SeasonPredictionSupport seasonPredictionSupport;

    @Mock
    LatestResultSupport latestResultSupport;

    @Mock
    ImpersonationGuard impersonationGuard;

    @Mock
    CurrentUserFacade currentUserFacade;

    @Mock
    RequestEmailVerificationUseCase requestEmailVerificationUseCase;

    private ProfileController controller;
    private WebUserDetails userDetails;

    private User optedInUser;

    @BeforeEach
    void setup() {
        controller = new ProfileController(
                userRepo,
                passwordHasher,
                seasonPredictionSupport,
                new CompetitionDefaults("premier-league"),
                latestResultSupport,
                impersonationGuard,
                currentUserFacade,
                requestEmailVerificationUseCase);

        UUID userId = UUID.randomUUID();
        optedInUser = User.builder()
                .id(userId)
                .publicId(PublicId.create("abcdefghjk"))
                .email(Email.create("alice@example.com"))
                .displayName("Alice")
                .roles(Set.of(Role.PLAYER))
                .resultsEmailOptOut(false)
                .build();

        userDetails = new WebUserDetails(userId, "abcdefghjk", "alice@example.com", "Alice", "", Set.of());

        when(currentUserFacade.isImpersonating()).thenReturn(false);
        when(userRepo.findById(userId)).thenReturn(Optional.of(optedInUser));
        when(seasonPredictionSupport.buildShareData(any(), any()))
                .thenReturn(new SeasonPredictionSupport.ShareData(false, null, null));
    }

    @Test
    void getSettingsChecksBoxWhenNotOptedOut() {
        Model model = new ExtendedModelMap();

        controller.profile(userDetails, model);

        var form = (ProfileController.ProfileForm) model.getAttribute("profileForm");
        assertThat(form.isReceiveResultsEmail()).isTrue();
    }

    @Test
    void getSettingsUnchecksBoxWhenOptedOut() {
        User optedOutUser = optedInUser.withResultsEmailOptOut(true);
        when(userRepo.findById(optedInUser.getId())).thenReturn(Optional.of(optedOutUser));
        Model model = new ExtendedModelMap();

        controller.profile(userDetails, model);

        var form = (ProfileController.ProfileForm) model.getAttribute("profileForm");
        assertThat(form.isReceiveResultsEmail()).isFalse();
    }

    @Test
    void submittingUncheckedBoxSetsOptOut() {
        ProfileController.ProfileForm form = new ProfileController.ProfileForm();
        form.setDisplayName("Alice");
        form.setReceiveResultsEmail(false); // box unchecked on submit

        controller.updateProfile(
                userDetails,
                form,
                noErrors(form),
                new MockHttpSession(),
                new RedirectAttributesModelMap(),
                new ExtendedModelMap());

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepo).update(savedUser.capture());
        assertThat(savedUser.getValue().isResultsEmailOptOut()).isTrue();
    }

    @Test
    void submittingCheckedBoxClearsOptOut() {
        User previouslyOptedOut = optedInUser.withResultsEmailOptOut(true);
        when(userRepo.findById(optedInUser.getId())).thenReturn(Optional.of(previouslyOptedOut));

        ProfileController.ProfileForm form = new ProfileController.ProfileForm();
        form.setDisplayName("Alice");
        form.setReceiveResultsEmail(true); // re-checked on submit

        controller.updateProfile(
                userDetails,
                form,
                noErrors(form),
                new MockHttpSession(),
                new RedirectAttributesModelMap(),
                new ExtendedModelMap());

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        org.mockito.Mockito.verify(userRepo).update(savedUser.capture());
        assertThat(savedUser.getValue().isResultsEmailOptOut()).isFalse();
    }

    private BindingResult noErrors(Object target) {
        return new BeanPropertyBindingResult(target, "profileForm");
    }
}
