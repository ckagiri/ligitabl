package com.ligitabl.api.notification.outbox;

import java.util.UUID;

import com.ligitabl.model.domain.HitDistribution;

/**
 * JSON payload of a ROUND_RESULTS outbox event: everything the relay needs to
 * render and send one user's round-results email without further queries.
 */
public record RoundResultsPayload(
        UUID userId,
        String userEmail,
        String userDisplayName,
        String userPublicId,
        String seasonSlug,
        int round,
        int score,
        int currentRound,
        int lastRound,
        HitDistribution hitDistribution,
        SprintPlacement sprint,
        SeasonPlacement season,
        Placement quarter) {

    /** Sprint standing, with best/movement. */
    public record SprintPlacement(
            String label,
            int fromRound,
            int toRound,
            int rank,
            int totalParticipants,
            Integer movement,
            int sprintBest,
            boolean isNewSprintBest) {}

    public record SeasonPlacement(
            String label,
            int fromRound,
            int toRound,
            int rank,
            int totalParticipants,
            Integer movement,
            int seasonBest,
            boolean isNewSeasonBest) {}

    /** Secondary standing (quarter) — rank only, no best/movement. */
    public record Placement(String label, int rank, int totalParticipants) {}
}
