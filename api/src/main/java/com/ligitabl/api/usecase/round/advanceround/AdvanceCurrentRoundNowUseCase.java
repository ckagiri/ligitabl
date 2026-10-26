package com.ligitabl.api.usecase.round.advanceround;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.usecase.shared.HierarchyValidator;
import com.ligitabl.model.repo.RoundRepo;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdvanceCurrentRoundNowUseCase {

    private final HierarchyValidator hierarchyValidator;
    private final CompetitionDefaults competitionDefaults;
    private final RoundAdvancementService roundAdvancementService;
    private final RoundRepo roundRepo;

    @Transactional
    public Either<UseCaseError, AdvanceCurrentRoundNowResult> execute() {
        record AdvanceAttempt(UUID roundId, boolean advanced) {}

        return hierarchyValidator
                .resolveHierarchy(competitionDefaults.defaultCompetitionSlug())
                .flatMap(ctx -> Either.catching(
                        () -> {
                            var roundId = ctx.round().getId();
                            var advanced = roundAdvancementService.advanceManually(roundId);
                            return new AdvanceAttempt(roundId, advanced);
                        },
                        UseCaseErrors::fromException))
                .flatMap(attempt -> {
                    if (!attempt.advanced()) {
                        return Either.left(UseCaseErrors.conflict("Round already advanced"));
                    }
                    return roundRepo
                            .findById(attempt.roundId())
                            .map(r -> Either.<UseCaseError, AdvanceCurrentRoundNowResult>right(
                                    new AdvanceCurrentRoundNowResult(
                                            r.getId(), r.getPosition(), r.getPosition() + 1, r.getAdvancedAt())))
                            .orElseGet(
                                    () -> Either.left(UseCaseErrors.unexpected("Round not found after advancement")));
                });
    }
}
