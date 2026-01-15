package com.ligitabl.api.usecases.season.seasonsetupmode;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.usecases.shared.HierarchyValidator;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.RoundSubmissionRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManageSeasonSetupModeUseCase
        implements UseCase<SeasonSetupModeCommand, Either<UseCaseError, SetupModeResult>> {

    private final SeasonRepo seasonRepo;
    private final RoundSubmissionRepo roundSubmissionRepo;
    private final HierarchyValidator hierarchyValidator;
    private final CompetitionDefaults competitionDefaults;
    private final Clock clock;

    @Override
    @Transactional
    public Either<UseCaseError, SetupModeResult> execute(SeasonSetupModeCommand cmd) {
        String competition = cmd.getCompetitionIdentifier().orElseGet(competitionDefaults::defaultCompetitionSlug);

        return hierarchyValidator
                .validateCompetitionAndSeason(competition, cmd.getSeasonSlug())
                .flatMap(season ->
                        cmd.getAction() == SetupModeAction.ENTER ? enterSetupMode(season) : leaveSetupMode(season));
    }

    private Either<UseCaseError, SetupModeResult> enterSetupMode(Season season) {
        if (season.isInSetupMode()) {
            return Either.left(UseCaseErrors.validation("Season is already in setup mode"));
        }

        if (roundSubmissionRepo.existsBySeasonId(season.getId())) {
            return Either.left(
                    UseCaseErrors.validation(
                            "Cannot enter setup mode: season has submissions. Setup mode is only for initial fixture arrangement."));
        }

        try {
            season.enterSetupMode();
        } catch (IllegalStateException e) {
            return Either.left(UseCaseErrors.validation(e.getMessage()));
        }
        Season saved = seasonRepo.save(season);
        Instant now = clock.instant();

        return Either.right(SetupModeResult.builder()
                .seasonId(saved.getId())
                .seasonSlug(saved.getSlug().value())
                .isInSetupMode(saved.isInSetupMode())
                .mainContestId(saved.getMainContestId())
                .detachedContestId(saved.getDetachedContestId())
                .message("Season entered setup mode")
                .timestamp(now)
                .build());
    }

    private Either<UseCaseError, SetupModeResult> leaveSetupMode(Season season) {
        if (!season.isInSetupMode()) {
            return Either.left(UseCaseErrors.validation("Season is not in setup mode"));
        }

        try {
            season.leaveSetupMode();
        } catch (IllegalStateException e) {
            return Either.left(UseCaseErrors.validation(e.getMessage()));
        }
        Season saved = seasonRepo.save(season);
        Instant now = clock.instant();

        return Either.right(SetupModeResult.builder()
                .seasonId(saved.getId())
                .seasonSlug(saved.getSlug().value())
                .isInSetupMode(saved.isInSetupMode())
                .mainContestId(saved.getMainContestId())
                .detachedContestId(saved.getDetachedContestId())
                .message("Season left setup mode")
                .timestamp(now)
                .build());
    }
}
