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
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdvanceCurrentRoundNowUseCase {

    private final HierarchyValidator hierarchyValidator;
    private final CompetitionDefaults competitionDefaults;
    private final RoundAdvancementService roundAdvancementService;
    private final RoundRepo roundRepo;

    public record AdvanceNowResult(
            UUID roundId,
            int fromPosition,
            int toPosition,
            OffsetDateTime advancedAt) {}

    @Transactional
    public Either<UseCaseError, AdvanceNowResult> execute() {
        return hierarchyValidator
                .resolveHierarchy(competitionDefaults.defaultCompetitionSlug())
                .flatMap(ctx -> {
                    try {
                        boolean advanced = roundAdvancementService.advanceManually(ctx.round().getId());

                        if (!advanced) {
                            return Either.left(UseCaseErrors.conflict("Round already advanced"));
                        }

                        var updatedRound = roundRepo.findById(ctx.round().getId())
                                .orElseThrow(() -> new IllegalStateException("Round not found after advancement"));

                        return Either.right(new AdvanceNowResult(
                                updatedRound.getId(),
                                updatedRound.getPosition(),
                                updatedRound.getPosition() + 1,
                                updatedRound.getAdvancedAt()));

                    } catch (IllegalStateException e) {
                        return Either.left(UseCaseErrors.conflict(e.getMessage()));
                    } catch (Exception e) {
                        log.error("Unexpected error during manual round advancement", e);
                        return Either.left(UseCaseErrors.unexpected(e));
                    }
                });
    }
}
