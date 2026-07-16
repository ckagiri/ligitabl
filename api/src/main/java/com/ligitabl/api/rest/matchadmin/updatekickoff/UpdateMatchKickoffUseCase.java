package com.ligitabl.api.rest.matchadmin.updatekickoff;

import static com.ligitabl.api.shared.ValidationUtils.requireFound;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.repo.MatchRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateMatchKickoffUseCase {

    private final MatchRepo matchRepo;
    private final HierarchyValidator hierarchyValidator;
    private final CompetitionDefaults competitionDefaults;

    public record Command(Integer roundPosition, String matchSlug, OffsetDateTime newKickOff) {}

    public record Result(UUID matchId, String matchSlug, OffsetDateTime newKickOff) {}

    @Transactional
    public Either<UseCaseError, Result> execute(Command cmd) {
        log.info("Executing UpdateMatchKickoff: slug={}, kickOff={}", cmd.matchSlug(), cmd.newKickOff());

        return hierarchyValidator
                .resolveHierarchy(competitionDefaults.defaultCompetitionSlug(), cmd.roundPosition())
                .flatMap(ctx -> requireFound(
                        matchRepo.findByRoundIdAndSlug(ctx.round().getId(), cmd.matchSlug()),
                        UseCaseErrors.notFound("Match", "slug", cmd.matchSlug())))
                .flatMap(match -> {
                    if (match.getStatus() == MatchStatus.FINISHED || match.getStatus() == MatchStatus.LIVE) {
                        return Either.left(UseCaseErrors.validation("Cannot update kickoff for a "
                                + match.getStatus().name().toLowerCase() + " match"));
                    }
                    match.setKickOff(cmd.newKickOff());
                    var saved = matchRepo.save(match);
                    return Either.<UseCaseError, Result>right(
                            new Result(saved.getId(), saved.getSlug(), saved.getKickOff()));
                });
    }
}
