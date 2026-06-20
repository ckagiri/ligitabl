package com.ligitabl.api.rest.match.syncfromapi;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligitabl.api.client.FootballDataApiError;
import com.ligitabl.api.client.FootballDataClient;
import com.ligitabl.api.client.footballdata.Score;
import com.ligitabl.api.shared.Either;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;
import com.ligitabl.model.repo.MatchRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyncMatchFromApiUseCase {

    private final FootballDataClient footballDataClient;
    private final MatchRepo matchRepo;

    public record SyncMatchFromApiCommand(int clientId) {}

    public sealed interface SyncMatchFromApiError {
        record NotFound(int clientId) implements SyncMatchFromApiError {}

        record AlreadyFinished(int clientId) implements SyncMatchFromApiError {}

        record ApiError(FootballDataApiError cause) implements SyncMatchFromApiError {}
    }

    @Transactional
    public Either<SyncMatchFromApiError, Void> execute(SyncMatchFromApiCommand command) {
        log.info("Syncing match from API: clientId={}", command.clientId());

        var matchOpt = matchRepo.findByClientId(command.clientId());
        if (matchOpt.isEmpty()) {
            return Either.left(new SyncMatchFromApiError.NotFound(command.clientId()));
        }

        var match = matchOpt.get();

        if (match.getStatus() == MatchStatus.FINISHED) {
            return Either.left(new SyncMatchFromApiError.AlreadyFinished(command.clientId()));
        }

        var apiResult = footballDataClient.getMatchById(command.clientId());
        if (apiResult.isLeft()) {
            return Either.left(new SyncMatchFromApiError.ApiError(apiResult.getLeft()));
        }

        var apiMatch = apiResult.get();
        applyUpdate(match, apiMatch);
        matchRepo.save(match);

        log.info("Synced match clientId={}: status={}", command.clientId(), match.getStatus());
        return Either.right(null);
    }

    private void applyUpdate(Match existing, com.ligitabl.api.client.footballdata.MatchDto apiMatch) {
        var newStatus = mapToDomainStatus(apiMatch.status());

        boolean statusChanged = existing.getStatus() != newStatus;
        boolean scoreChanged = hasScoreChanged(existing, apiMatch.score());
        boolean kickoffChanged = hasKickoffChanged(existing, apiMatch.utcDate());

        if (!statusChanged && !scoreChanged && !kickoffChanged) {
            return;
        }

        existing.setStatus(newStatus);
        if (kickoffChanged) {
            existing.setKickOff(apiMatch.utcDate());
        }
        if (apiMatch.matchday() != null) {
            existing.setMatchday(apiMatch.matchday());
        }
        applyScore(existing, apiMatch.score());
    }

    private boolean hasKickoffChanged(Match existing, OffsetDateTime apiKickoff) {
        var existingKickoff = existing.getKickOff();
        if (existingKickoff == null || apiKickoff == null) {
            return existingKickoff != apiKickoff;
        }
        boolean dateChanged = !existingKickoff.toLocalDate().equals(apiKickoff.toLocalDate());
        boolean timeChanged = !existingKickoff.toLocalTime().equals(apiKickoff.toLocalTime());
        return dateChanged || timeChanged;
    }

    private boolean hasScoreChanged(Match existing, Score apiScore) {
        var apiGoals = extractGoals(apiScore);
        if (apiGoals == null) return false;
        var existingScore = existing.getScore();
        if (existingScore == null) return true;
        return existingScore.getHomeGoals() != apiGoals[0] || existingScore.getAwayGoals() != apiGoals[1];
    }

    private void applyScore(Match existing, Score apiScore) {
        var apiGoals = extractGoals(apiScore);
        if (apiGoals != null) {
            existing.setScore(apiGoals[0], apiGoals[1]);
        }
    }

    private Integer[] extractGoals(Score apiScore) {
        if (apiScore == null || apiScore.fullTime() == null) return null;
        Integer home = apiScore.fullTime().home();
        Integer away = apiScore.fullTime().away();
        if (home == null || away == null) return null;
        return new Integer[] {home, away};
    }

    private MatchStatus mapToDomainStatus(String status) {
        return switch (status) {
            case "SCHEDULED", "TIMED" -> MatchStatus.SCHEDULED;
            case "IN_PLAY", "PAUSED" -> MatchStatus.LIVE;
            case "FINISHED", "AWARDED" -> MatchStatus.FINISHED;
            case "SUSPENDED" -> MatchStatus.SUSPENDED;
            case "POSTPONED" -> MatchStatus.POSTPONED;
            case "CANCELLED" -> MatchStatus.CANCELLED;
            default -> {
                log.warn("Unknown match status from API: {}", status);
                yield MatchStatus.SCHEDULED;
            }
        };
    }
}
