package com.ligitabl.api.usecase.admin.deleteuser;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteUserUseCase {

    private final UserRepo userRepo;
    private final SeasonPredictionRepo seasonPredictionRepo;
    private final EntryRepo entryRepo;
    private final RoundResultRepo roundResultRepo;
    private final RoundSubmissionRepo roundSubmissionRepo;
    private final PasswordResetTokenRepo passwordResetTokenRepo;
    private final ContestRepo contestRepo;
    private final WhatIfPredictionRepo whatIfPredictionRepo;
    private final EmailVerificationTokenRepo emailVerificationTokenRepo;
    private final FinalTablePredictionRepo finalTablePredictionRepo;

    @Transactional
    public DeleteUserResult execute(UUID userId, UUID currentSeasonId) {
        User user = userRepo.findById(userId).orElse(null);
        if (user == null) {
            return new DeleteUserResult.UserNotFound(userId);
        }

        int totalPredictions =
                seasonPredictionRepo.countByUserIds(List.of(userId)).getOrDefault(userId, 0);
        boolean hasCurrentSeasonPrediction =
                currentSeasonId != null && seasonPredictionRepo.existsByUserAndSeason(userId, currentSeasonId);
        int pastPredictions = totalPredictions - (hasCurrentSeasonPrediction ? 1 : 0);
        int currentSeasonSwaps = seasonPredictionRepo
                .sumSwapCountsByUserIdsAndSeason(List.of(userId), currentSeasonId)
                .getOrDefault(userId, 0);

        if (pastPredictions > 0 || currentSeasonSwaps > 0) {
            return new DeleteUserResult.NotEligible(userId, pastPredictions, currentSeasonSwaps);
        }

        if (contestRepo.existsByOwnerId(userId)) {
            return new DeleteUserResult.OwnsContest(userId);
        }

        // Every table with an FK to t_user must be cleared first: only t_user_role cascades.
        entryRepo.deleteByUserId(userId);
        passwordResetTokenRepo.deleteAllForUser(userId);
        emailVerificationTokenRepo.deleteAllForUser(userId);
        finalTablePredictionRepo.deleteByUserId(userId);
        roundResultRepo.deleteByUserId(userId);
        roundSubmissionRepo.deleteByUserId(userId);
        whatIfPredictionRepo.deleteByUserId(userId);
        seasonPredictionRepo.deleteByUserId(userId);
        userRepo.delete(userId);

        log.info(
                "[ADMIN_DELETE_USER] userId={} email={}",
                userId,
                user.getEmail().value());

        return new DeleteUserResult.Ok(userId);
    }
}
