package com.ligitabl.api.scheduling.syncmatches;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ligitabl.model.domain.Match;
import com.ligitabl.model.domain.MatchStatus;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class LiveMatchTracker {

    private Set<UUID> currentLiveMatches = new HashSet<>();

    public TrackingResult updateTracking(List<Match> matches) {
        var newLiveMatches = matches.stream()
                .filter(m -> m.getStatus() == MatchStatus.LIVE)
                .map(Match::getId)
                .collect(Collectors.toSet());

        var finishedMatches = currentLiveMatches.stream()
                .filter(id -> !newLiveMatches.contains(id))
                .collect(Collectors.toSet());

        var startedMatches = newLiveMatches.stream()
                .filter(id -> !currentLiveMatches.contains(id))
                .collect(Collectors.toSet());

        if (!finishedMatches.isEmpty()) {
            log.info("Detected {} matches transitioning from LIVE: {}", finishedMatches.size(), finishedMatches);
        }

        if (!startedMatches.isEmpty()) {
            log.info("Detected {} new LIVE matches: {}", startedMatches.size(), startedMatches);
        }

        currentLiveMatches = newLiveMatches;

        return new TrackingResult(!newLiveMatches.isEmpty(), finishedMatches, startedMatches);
    }

    public record TrackingResult(boolean hasLive, Set<UUID> finishedMatches, Set<UUID> startedMatches) {
        public boolean needsTodayFetch() {
            return !finishedMatches.isEmpty();
        }
    }
}
