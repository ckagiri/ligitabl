package com.ligitabl.api.usecase.match.shared;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

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
     * Applies feed data to an existing match. Returns true if any field changed.
     */
    public boolean applyUpdate(Match existing, FeedMatch feedMatch) {
        var newStatus = feedMatch.status();

        // football-data has reported results as FINISHED and then flipped them back to TIMED with null
        // scores (2026-10-24). A finished match never legitimately restarts, so treat it as stale data.
        if (existing.getStatus() == MatchStatus.FINISHED
                && (newStatus == MatchStatus.SCHEDULED || newStatus == MatchStatus.LIVE)) {
            if (existing.getClientId() != null && regressionsReported.add(existing.getClientId())) {
                log.warn(
                        "Ignoring feed regression FINISHED -> {} for match clientId={}",
                        newStatus,
                        existing.getClientId());
                Sentry.captureMessage(
                        "Match feed regressed FINISHED match " + existing.getClientId() + " to " + newStatus);
            }
            return false;
        }

        boolean statusChanged = existing.getStatus() != newStatus;
        boolean scoreChanged = hasScoreChanged(existing, feedMatch);
        boolean kickoffChanged = hasKickoffChanged(existing, feedMatch.kickoff());
        boolean minuteChanged = !Objects.equals(existing.getMinute(), feedMatch.minute());
        boolean injuryTimeChanged = !Objects.equals(existing.getInjuryTime(), feedMatch.injuryTime());

        if (!statusChanged && !scoreChanged && !kickoffChanged && !minuteChanged && !injuryTimeChanged) {
            return false;
        }

        existing.setStatus(newStatus);
        if (kickoffChanged) {
            existing.setKickOff(feedMatch.kickoff());
        }
        if (feedMatch.matchday() != null) {
            existing.setMatchday(feedMatch.matchday());
        }
        existing.setMinute(feedMatch.minute());
        existing.setInjuryTime(feedMatch.injuryTime());
        applyScore(existing, feedMatch);

        return true;
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

    private boolean hasScoreChanged(Match existing, FeedMatch feedMatch) {
        if (!hasScore(feedMatch)) return false;
        var existingScore = existing.getScore();
        if (existingScore == null) return true;
        return existingScore.getHomeGoals() != feedMatch.homeGoals()
                || existingScore.getAwayGoals() != feedMatch.awayGoals();
    }

    private void applyScore(Match existing, FeedMatch feedMatch) {
        if (hasScore(feedMatch)) {
            existing.setScore(feedMatch.homeGoals(), feedMatch.awayGoals());
        }
    }

    private static boolean hasScore(FeedMatch feedMatch) {
        return feedMatch.homeGoals() != null && feedMatch.awayGoals() != null;
    }
}
