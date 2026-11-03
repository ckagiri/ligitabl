package com.ligitabl.api.usecase.round.advanceround;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.usecase.shared.HierarchyValidator;
import com.ligitabl.model.shared.RuleViolationException;

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
                .flatMap(ctx -> {
                    var roundId = ctx.round().getId();
                    try {
                        roundAdvancementService.cancelScheduledAdvancement(roundId);
                    } catch (RuleViolationException e) {
                        return Either.left(UseCaseErrors.conflict(e.getMessage()));
                    }
                    return Either.right(new CancelRoundAdvancementResult(
                            roundId,
                            "Automatic advancement cancelled. Round remains FINALIZED. Use advance-now when ready."));
                });
    }
}
