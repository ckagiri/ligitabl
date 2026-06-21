package com.ligitabl.api.rest.match.syncfromapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.client.FootballDataApiError;
import com.ligitabl.api.client.FootballDataClient;
import com.ligitabl.api.config.CompetitionDefaults;
import com.ligitabl.api.rest.match.MatchUpdateHelper;
import com.ligitabl.api.rest.shared.HierarchyValidator;
import com.ligitabl.api.shared.Either;
import com.ligitabl.api.shared.errors.UseCaseError;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.repo.MatchRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncRoundUseCase {

    private final HierarchyValidator hierarchyValidator;
    private final MatchRepo matchRepo;
    private final FootballDataClient footballDataClient;
    private final MatchUpdateHelper matchUpdateHelper;
    private final CompetitionDefaults competitionDefaults;

    @Value("${football-data.competition.code}")
    private String competitionCode;

    public record SyncRoundCommand() {}

    public sealed interface SyncRoundError {
        record HierarchyError(UseCaseError cause) implements SyncRoundError {}

        record ApiError(FootballDataApiError cause) implements SyncRoundError {}
    }

    @Transactional
    public Either<SyncRoundError, Void> execute(SyncRoundCommand command) {
        log.info("Syncing non-finished round matches from API");

        return hierarchyValidator
                .resolveHierarchy(competitionDefaults.defaultCompetitionSlug())
                .mapLeft(e -> (SyncRoundError) new SyncRoundError.HierarchyError(e))
                .flatMap(ctx -> {
                    if (ctx.round().isFinalized()) {
                        log.info("Round {} is already finalized, skipping sync", ctx.round().getPosition());
                        return Either.right(null);
                    }

                    var allMatches = matchRepo.findByRoundId(ctx.round().getId());
                    var nonFinished = allMatches.stream()
                            .filter(m -> m.getStatus() != MatchStatus.FINISHED)
                            .toList();

                    if (nonFinished.isEmpty()) {
                        log.info("No non-finished matches in round {}", ctx.round().getPosition());
                        return Either.right(null);
                    }

                    var clientIds = nonFinished.stream()
                            .map(m -> m.getClientId())
                            .filter(id -> id != null)
                            .toList();

                    log.info("Fetching {} non-finished matches by IDs from API", clientIds.size());

                    var apiResult = footballDataClient.getMatchesByIds(competitionCode, clientIds);
                    if (apiResult.isLeft()) {
                        return Either.left(new SyncRoundError.ApiError(apiResult.getLeft()));
                    }

                    var apiMatches = apiResult.get().matches();
                    int updated = 0;
                    for (var apiMatch : apiMatches) {
                        var dbMatch = nonFinished.stream()
                                .filter(m -> m.getClientId() != null
                                        && m.getClientId().longValue() == apiMatch.id())
                                .findFirst()
                                .orElse(null);

                        if (dbMatch != null && matchUpdateHelper.applyUpdate(dbMatch, apiMatch)) {
                            matchRepo.save(dbMatch);
                            updated++;
                        }
                    }

                    log.info("Synced round {}: {} matches updated", ctx.round().getPosition(), updated);
                    return Either.<SyncRoundError, Void>right(null);
                });
    }
}
