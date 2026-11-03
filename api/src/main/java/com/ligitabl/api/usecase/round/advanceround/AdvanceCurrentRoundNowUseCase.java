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
import com.ligitabl.model.shared.RuleViolationException;

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
                .flatMap(ctx -> {
                    var roundId = ctx.round().getId();
                    try {
                        var advanced = roundAdvancementService.advanceManually(roundId);
                        return Either.<UseCaseError, AdvanceAttempt>right(new AdvanceAttempt(roundId, advanced));
                    } catch (RuleViolationException e) {
                        return Either.left(UseCaseErrors.conflict(e.getMessage()));
                    }
                })
                .flatMap(attempt -> {
                    if (!attempt.advanced()) {
                        return Either.left(UseCaseErrors.conflict("Round already advanced"));
                    }
                    var r = roundRepo
                            .findById(attempt.roundId())
                            .orElseThrow(() -> new IllegalStateException("Round not found after advancement"));
                    return Either.right(new AdvanceCurrentRoundNowResult(
                            r.getId(), r.getPosition(), r.getPosition() + 1, r.getAdvancedAt()));
                });
    }
}
