package com.ligitabl.api.notification.outbox;

import java.util.UUID;

import com.ligitabl.model.domain.HitDistribution;

/**
 * JSON payload of a ROUND_RESULTS outbox event: everything the relay needs to
 * render and send one user's round-results email without further queries.
 *
 * <p>The email shows a "best"/movement callout for both sprint and quarter —
 * recipients are selected from the sprint leaderboard (top-N per round), and
 * the content covers both phases the recipient is scored in. Full-season is
 * shown as a plain secondary standing (rank only, no "season best" concept).
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
        QuarterPlacement quarter,
        Placement season) {

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

    /** Quarter standing, with best/movement. Null when no quarter phase contains this round. */
    public record QuarterPlacement(
            String label,
            int fromRound,
            int toRound,
            int rank,
            int totalParticipants,
            Integer movement,
            int quarterBest,
            boolean isNewQuarterBest) {}

    /** Secondary standing (full season) — rank only, no best/movement. */
    public record Placement(String label, int rank, int totalParticipants) {}
}
