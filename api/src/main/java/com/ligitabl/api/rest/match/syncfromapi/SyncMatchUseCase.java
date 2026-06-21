package com.ligitabl.api.rest.match.syncfromapi;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.client.FootballDataApiError;
import com.ligitabl.api.client.FootballDataClient;
import com.ligitabl.api.rest.match.MatchUpdateHelper;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.repo.MatchRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncMatchUseCase {

    private final FootballDataClient footballDataClient;
    private final MatchRepo matchRepo;
    private final MatchUpdateHelper matchUpdateHelper;

    public record SyncMatchCommand(int clientId) {}

    public sealed interface SyncMatchError {
        record NotFound(int clientId) implements SyncMatchError {}

        record AlreadyFinished(int clientId) implements SyncMatchError {}

        record ApiError(FootballDataApiError cause) implements SyncMatchError {}
    }

    @Transactional
    public Either<SyncMatchError, Void> execute(SyncMatchCommand command) {
        log.info("Syncing match from API: clientId={}", command.clientId());

        var matchOpt = matchRepo.findByClientId(command.clientId());
        if (matchOpt.isEmpty()) {
            return Either.left(new SyncMatchError.NotFound(command.clientId()));
        }

        var match = matchOpt.get();

        if (match.getStatus() == MatchStatus.FINISHED) {
            return Either.left(new SyncMatchError.AlreadyFinished(command.clientId()));
        }

        var apiResult = footballDataClient.getMatchById(command.clientId());
        if (apiResult.isLeft()) {
            return Either.left(new SyncMatchError.ApiError(apiResult.getLeft()));
        }

        matchUpdateHelper.applyUpdate(match, apiResult.get());
        matchRepo.save(match);

        log.info("Synced match clientId={}: status={}", command.clientId(), match.getStatus());
        return Either.right(null);
    }
}
