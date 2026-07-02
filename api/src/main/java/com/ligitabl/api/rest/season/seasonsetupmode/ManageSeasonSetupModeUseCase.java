package com.ligitabl.api.rest.season.seasonsetupmode;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.UseCase;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.model.domain.Round;
import com.ligitabl.model.domain.Season;
import com.ligitabl.model.repo.RoundRepo;
import com.ligitabl.model.repo.SeasonRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManageSeasonSetupModeUseCase
        implements UseCase<SeasonSetupModeCommand, Either<UseCaseError, SetupModeResult>> {

    private final SeasonRepo seasonRepo;
    private final RoundRepo roundRepo;
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

        return findOutOfSyncRounds(season).flatMap(outOfSyncRounds -> {
            if (!outOfSyncRounds.isEmpty()) {
                String positions = outOfSyncRounds.stream()
                        .map(r -> String.valueOf(r.getPosition()))
                        .collect(Collectors.joining(", "));
                return Either.left(UseCaseErrors.validation(String.format(
                        "Cannot exit setup mode: round(s) %s are out of sync. Refinalize them first.", positions)));
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
        });
    }

    // Rounds are marked unfinalized by the setup-mode reschedule/transition/refinalize cascades
    // whenever a past-round correction is made; leaving setup mode with any of those still
    // unresolved would silently ship stale standings once contests reattach.
    private Either<UseCaseError, List<Round>> findOutOfSyncRounds(Season season) {
        return hierarchyValidator.validateCurrentRound(season).map(currentRound -> {
            int currentPosition = currentRound.getPosition();
            return roundRepo.findBySeasonIdOrderByPosition(season.getId()).stream()
                    .filter(r -> r.getPosition() < currentPosition && !r.isFinalized())
                    .toList();
        });
    }
}
