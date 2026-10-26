package com.ligitabl.api.usecase.round.advanceround;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.usecase.shared.HierarchyValidator;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CancelRoundAdvancementUseCase {

    private final HierarchyValidator hierarchyValidator;
    private final CompetitionDefaults competitionDefaults;
    private final RoundAdvancementService roundAdvancementService;

    @Transactional
    public Either<UseCaseError, CancelRoundAdvancementResult> execute() {
        return hierarchyValidator
                .resolveHierarchy(competitionDefaults.defaultCompetitionSlug())
                .flatMap(ctx -> Either.catching(
                        () -> {
                            roundAdvancementService.cancelScheduledAdvancement(
                                    ctx.round().getId());
                            return new CancelRoundAdvancementResult(
                                    ctx.round().getId(),
                                    "Automatic advancement cancelled. Round remains FINALIZED. Use advance-now when ready.");
                        },
                        UseCaseErrors::fromException));
    }
}
