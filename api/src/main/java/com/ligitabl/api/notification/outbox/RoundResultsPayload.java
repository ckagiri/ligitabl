package com.ligitabl.api.notification.outbox;

import java.util.UUID;

import com.ligitabl.model.domain.HitDistribution;

/**
 * JSON payload of a ROUND_RESULTS outbox event: everything the relay needs to
 * render and send one user's round-results email without further queries.
 *
 * <p>Mirrors the in-app results banner (score, hit distribution, sprint
 * progress) plus the user's placement on the sprint, quarter and full-season
 * leaderboards. Placements are the real in-app leaderboard positions —
 * ignore-list users keep their ranks, they just don't receive email.
 */
public record RoundResultsPayload(
        UUID userId,
        String userEmail,
        String userDisplayName,
        int round,
        int score,
        int currentRound,
        int lastRound,
        HitDistribution hitDistribution,
        SprintPlacement sprint,
        Placement quarter,
        Placement season) {

    /** Sprint standing — the email's main focus. */
    public record SprintPlacement(
            String label,
            int fromRound,
            int toRound,
            int rank,
            int totalParticipants,
            Integer movement,
            int sprintBest,
            boolean isNewSprintBest) {}

    /** Secondary standing (quarter / full season). Null when the phase isn't configured. */
    public record Placement(String label, int rank, int totalParticipants) {}
}
