package com.ligitabl.api.rest.match;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.ligitabl.api.client.footballdata.MatchDto;
import com.ligitabl.api.client.footballdata.Score;
import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;

import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class MatchUpdateHelper {

    /** Report each regressed match once per JVM; the sync polls every minute while the API is stale. */
    private final Set<Integer> regressionsReported = ConcurrentHashMap.newKeySet();

    /**
     * Applies API data to an existing match. Returns true if any field changed.
     */
    public boolean applyUpdate(Match existing, MatchDto apiMatch) {
        var newStatus = mapToDomainStatus(apiMatch.status());

        // football-data has reported results as FINISHED and then flipped them back to TIMED with null
        // scores (2026-10-24). A finished match never legitimately restarts, so treat it as stale data.
        if (existing.getStatus() == MatchStatus.FINISHED
                && (newStatus == MatchStatus.SCHEDULED || newStatus == MatchStatus.LIVE)) {
            if (existing.getClientId() != null && regressionsReported.add(existing.getClientId())) {
                log.warn(
                        "Ignoring API regression FINISHED -> {} for match clientId={}",
                        apiMatch.status(),
                        existing.getClientId());
                Sentry.captureMessage("football-data regressed FINISHED match " + existing.getClientId() + " to "
                        + apiMatch.status());
            }
            return false;
        }

        boolean statusChanged = existing.getStatus() != newStatus;
        boolean scoreChanged = hasScoreChanged(existing, apiMatch.score());
        boolean kickoffChanged = hasKickoffChanged(existing, apiMatch.utcDate());
        boolean minuteChanged = !Objects.equals(existing.getMinute(), apiMatch.minute());
        boolean injuryTimeChanged = !Objects.equals(existing.getInjuryTime(), apiMatch.injuryTime());

        if (!statusChanged && !scoreChanged && !kickoffChanged && !minuteChanged && !injuryTimeChanged) {
            return false;
        }

        existing.setStatus(newStatus);
        if (kickoffChanged) {
            existing.setKickOff(apiMatch.utcDate());
        }
        if (apiMatch.matchday() != null) {
            existing.setMatchday(apiMatch.matchday());
        }
        existing.setMinute(apiMatch.minute());
        existing.setInjuryTime(apiMatch.injuryTime());
        applyScore(existing, apiMatch.score());

        return true;
    }

    public MatchStatus mapToDomainStatus(String status) {
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
}
