package com.ligitabl.api.rest.round.advanceround;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.scheduling.advanceround.RoundAdvancementService;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CancelRoundAdvancementUseCase {

    private final HierarchyValidator hierarchyValidator;
    private final CompetitionDefaults competitionDefaults;
    private final RoundAdvancementService roundAdvancementService;

    public record CancelResult(UUID roundId, String message) {}

    @Transactional
    public Either<UseCaseError, CancelResult> execute() {
        return hierarchyValidator
                .resolveHierarchy(competitionDefaults.defaultCompetitionSlug())
                .flatMap(ctx -> Either.catching(
                        () -> {
                            roundAdvancementService.cancelScheduledAdvancement(
                                    ctx.round().getId());
                            return new CancelResult(
                                    ctx.round().getId(),
                                    "Automatic advancement cancelled. Round remains FINALIZED. Use advance-now when ready.");
                        },
                        UseCaseErrors::fromException));
    }
}
