package com.ligitabl.api.notification.outbox;

import java.util.List;
import java.util.UUID;

/**
 * JSON payload of a SEGMENT_RESULTS outbox event: everything the relay needs to render and send
 * one user's segment-success email without further queries.
 *
 * <p>Every entry in {@link #placements()} is a <em>closed</em> window the user finished top 3 in.
 *
 * @param scopeKey distinguishes the two boundaries that can share a round number — {@code "r38"}
 *     for the round-38 sprint/quarter close, {@code "season"} for the later season completion.
 */
public record SegmentResultsPayload(
        UUID userId,
        String userEmail,
        String userDisplayName,
        String userPublicId,
        String seasonSlug,
        String scopeKey,
        int boundaryRound,
        List<SegmentPlacement> placements) {

    /** One finished segment the user placed in, ordered sprint → quarter → season. */
    public record SegmentPlacement(
            String type,
            String code,
            String name,
            int fromRound,
            int toRound,
            int rank,
            int totalParticipants,
            int totalScore) {}
}
