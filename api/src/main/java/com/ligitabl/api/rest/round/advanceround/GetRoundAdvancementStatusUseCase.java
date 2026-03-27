package com.ligitabl.api.rest.round.advanceround;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.model.domain.RoundStatus;
import com.ligitabl.model.repo.MatchRepo;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GetRoundAdvancementStatusUseCase {

    private final HierarchyValidator hierarchyValidator;
    private final CompetitionDefaults competitionDefaults;
    private final Clock clock;
    private final MatchRepo matchRepo;

    public record AdvancementStatusResult(
            UUID roundId,
            int position,
            RoundStatus status,
            boolean scheduled,
            OffsetDateTime advanceAt,
            Integer minutesRemaining,
            boolean advanced,
            boolean canOverride) {}

    public Either<UseCaseError, AdvancementStatusResult> execute() {
        return hierarchyValidator
                .resolveHierarchy(competitionDefaults.defaultCompetitionSlug())
                .map(ctx -> {
                    var round = ctx.round();
                    boolean scheduled = round.getAdvanceAt() != null && !round.isAdvanced();

                    Integer minutesRemaining = null;
                    if (scheduled) {
                        var now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
                        if (round.getAdvanceAt().isAfter(now)) {
                            minutesRemaining = (int)
                                    Duration.between(now, round.getAdvanceAt()).toMinutes();
                        } else {
                            minutesRemaining = 0;
                        }
                    }

                    var matches =
                            (round.isAdvanced() || round.isFinalized()) ? null : matchRepo.findByRoundId(round.getId());

                    return new AdvancementStatusResult(
                            round.getId(),
                            round.getPosition(),
                            round.computeStatus(matches),
                            scheduled,
                            round.getAdvanceAt(),
                            minutesRemaining,
                            round.isAdvanced(),
                            !round.isAdvanced());
                });
    }
}
