package com.ligitabl.api.rest.admin.deleteuser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.ligitabl.model.auth.Email;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.auth.Role;
import com.ligitabl.model.domain.User;
import com.ligitabl.model.repo.ContestRepo;
import com.ligitabl.model.repo.EmailVerificationTokenRepo;
import com.ligitabl.model.repo.EntryRepo;
import com.ligitabl.model.repo.FinalTablePredictionRepo;
import com.ligitabl.model.repo.PasswordResetTokenRepo;
import com.ligitabl.model.repo.RoundResultRepo;
import com.ligitabl.model.repo.RoundSubmissionRepo;
import com.ligitabl.model.repo.SeasonPredictionRepo;
import com.ligitabl.model.repo.UserRepo;
import com.ligitabl.model.repo.WhatIfPredictionRepo;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeleteUserUseCaseTest {

    @Mock
    UserRepo userRepo;

    @Mock
    SeasonPredictionRepo seasonPredictionRepo;

    @Mock
    EntryRepo entryRepo;

    @Mock
    RoundResultRepo roundResultRepo;

    @Mock
    RoundSubmissionRepo roundSubmissionRepo;

    @Mock
    PasswordResetTokenRepo passwordResetTokenRepo;

    @Mock
    ContestRepo contestRepo;

    @Mock
    WhatIfPredictionRepo whatIfPredictionRepo;

    @Mock
    EmailVerificationTokenRepo emailVerificationTokenRepo;

    @Mock
    FinalTablePredictionRepo finalTablePredictionRepo;

    DeleteUserUseCase useCase;

    UUID userId;
    UUID seasonId;

    @BeforeEach
    void setUp() {
        useCase = new DeleteUserUseCase(
                userRepo,
                seasonPredictionRepo,
                entryRepo,
                roundResultRepo,
                roundSubmissionRepo,
                passwordResetTokenRepo,
                contestRepo,
                whatIfPredictionRepo,
                emailVerificationTokenRepo,
                finalTablePredictionRepo);

        userId = UUID.randomUUID();
        seasonId = UUID.randomUUID();

        User user = User.builder()
                .id(userId)
                .publicId(PublicId.create("AbCd3fGh9J"))
                .email(Email.create("test@example.com"))
                .displayName("Test User")
                .roles(Set.of(Role.PLAYER))
                .emailVerified(true)
                .build();

        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(seasonPredictionRepo.countByUserIds(any())).thenReturn(Map.of(userId, 1));
        when(seasonPredictionRepo.existsByUserAndSeason(userId, seasonId)).thenReturn(true);
        when(seasonPredictionRepo.sumSwapCountsByUserIdsAndSeason(any(), any())).thenReturn(Map.of());
        when(contestRepo.existsByOwnerId(userId)).thenReturn(false);
    }

    /**
     * Nine tables carry an FK to t_user and only t_user_role cascades, so every other one must be
     * cleared here. Missing t_email_verification_token is what broke admin deletion in production
     * (2026-09-04); t_final_table_prediction was the same bug not yet triggered.
     */
    @Test
    void deletesEveryChildTableBeforeTheUser() {
        Object result = useCase.execute(userId, seasonId);

        assertThat(result).isInstanceOf(DeleteUserUseCase.Result.Ok.class);

        InOrder inOrder = inOrder(
                entryRepo,
                passwordResetTokenRepo,
                emailVerificationTokenRepo,
                finalTablePredictionRepo,
                roundResultRepo,
                roundSubmissionRepo,
                whatIfPredictionRepo,
                seasonPredictionRepo,
                userRepo);

        inOrder.verify(entryRepo).deleteByUserId(userId);
        inOrder.verify(passwordResetTokenRepo).deleteAllForUser(userId);
        inOrder.verify(emailVerificationTokenRepo).deleteAllForUser(userId);
        inOrder.verify(finalTablePredictionRepo).deleteByUserId(userId);
        inOrder.verify(roundResultRepo).deleteByUserId(userId);
        inOrder.verify(roundSubmissionRepo).deleteByUserId(userId);
        inOrder.verify(whatIfPredictionRepo).deleteByUserId(userId);
        inOrder.verify(seasonPredictionRepo).deleteByUserId(userId);
        inOrder.verify(userRepo).delete(userId);
    }

    @Test
    void deletesNothing_whenUserNotFound() {
        UUID unknown = UUID.randomUUID();
        when(userRepo.findById(unknown)).thenReturn(Optional.empty());

        Object result = useCase.execute(unknown, seasonId);

        assertThat(result).isInstanceOf(DeleteUserUseCase.Result.UserNotFound.class);
        verifyNoChildDeletes();
    }

    @Test
    void deletesNothing_whenUserHasPastSeasonPredictions() {
        when(seasonPredictionRepo.countByUserIds(any())).thenReturn(Map.of(userId, 3));

        Object result = useCase.execute(userId, seasonId);

        assertThat(result).isInstanceOf(DeleteUserUseCase.Result.NotEligible.class);
        verifyNoChildDeletes();
    }

    @Test
    void deletesNothing_whenUserHasCurrentSeasonSwaps() {
        when(seasonPredictionRepo.sumSwapCountsByUserIdsAndSeason(any(), any())).thenReturn(Map.of(userId, 2));

        Object result = useCase.execute(userId, seasonId);

        assertThat(result).isInstanceOf(DeleteUserUseCase.Result.NotEligible.class);
        verifyNoChildDeletes();
    }

    @Test
    void deletesNothing_whenUserOwnsAContest() {
        when(contestRepo.existsByOwnerId(userId)).thenReturn(true);

        Object result = useCase.execute(userId, seasonId);

        assertThat(result).isInstanceOf(DeleteUserUseCase.Result.OwnsContest.class);
        verifyNoChildDeletes();
    }

    private void verifyNoChildDeletes() {
        verify(userRepo, never()).delete(any());
        verify(entryRepo, never()).deleteByUserId(any());
        verify(emailVerificationTokenRepo, never()).deleteAllForUser(any());
        verify(finalTablePredictionRepo, never()).deleteByUserId(any());
    }
}
