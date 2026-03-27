package com.ligitabl.api.rest.round.advanceround;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.scheduling.advanceround.RoundAdvancementService;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.model.repo.RoundRepo;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdvanceCurrentRoundNowUseCase {

    private final HierarchyValidator hierarchyValidator;
    private final CompetitionDefaults competitionDefaults;
    private final RoundAdvancementService roundAdvancementService;
    private final RoundRepo roundRepo;

    public record AdvanceNowResult(UUID roundId, int fromPosition, int toPosition, OffsetDateTime advancedAt) {}

    @Transactional
    public Either<UseCaseError, AdvanceNowResult> execute() {
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
                            .map(r -> Either.<UseCaseError, AdvanceNowResult>right(new AdvanceNowResult(
                                    r.getId(), r.getPosition(), r.getPosition() + 1, r.getAdvancedAt())))
                            .orElseGet(
                                    () -> Either.left(UseCaseErrors.unexpected("Round not found after advancement")));
                });
    }
}
