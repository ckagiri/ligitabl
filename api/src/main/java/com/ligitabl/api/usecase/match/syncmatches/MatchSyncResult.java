package com.ligitabl.api.usecase.match.syncmatches;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.ligitabl.model.domain.league.RoundStatus;

import lombok.Builder;

@Builder
public record MatchSyncResult(
        UUID seasonId,
        UUID roundId,
        int roundPosition,
        RoundStatus roundStatus,
        int matchesProcessed,
        int matchesUpdated,
        int newlyFinishedMatches,
        List<UUID> finishedMatchIds,
        boolean allMatchesComplete,
        boolean hasBlockingMatches,
        boolean roundObstructed,
        List<UUID> obstructedMatchIds,
        NextSyncSchedule nextSchedule) {
    public static MatchSyncResult empty() {
        return MatchSyncResult.builder()
                .seasonId(null)
                .roundId(null)
                .roundPosition(0)
                .roundStatus(RoundStatus.UNKNOWN)
                .matchesProcessed(0)
                .matchesUpdated(0)
                .newlyFinishedMatches(0)
                .finishedMatchIds(List.of())
                .allMatchesComplete(false)
                .hasBlockingMatches(false)
                .roundObstructed(false)
                .obstructedMatchIds(List.of())
                .nextSchedule(new NextSyncSchedule(Duration.ofHours(6), "No matches to sync"))
                .build();
    }
}
