package com.ligitabl.api.scheduling.sync;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Result of a match synchronization operation
 */
public record MatchSyncResult(
        int matchesProcessed,
        int matchesUpdated,
        int newlyFinishedMatches,
        List<UUID> finishedMatchIds,
        boolean allMatchesComplete,
        boolean hasBlockingMatches,
        NextSyncSchedule nextSchedule
) {
    public static MatchSyncResult empty() {
        return new MatchSyncResult(
                0, 0, 0,
                List.of(),
                false,
                false,
                new NextSyncSchedule(Duration.ofHours(6), "No matches to sync")
        );
    }
}
