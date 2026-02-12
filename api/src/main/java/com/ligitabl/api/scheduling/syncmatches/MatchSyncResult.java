package com.ligitabl.api.scheduling.syncmatches;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Result of a match synchronization operation
 */
public record MatchSyncResult(
        UUID seasonId,
        UUID roundId,
        int roundPosition,
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
        return new MatchSyncResult(
                null,
                null,
                0,
                0,
                0,
                0,
                List.of(),
                false,
                false,
                false,
                List.of(),
                new NextSyncSchedule(Duration.ofHours(6), "No matches to sync"));
    }
}
