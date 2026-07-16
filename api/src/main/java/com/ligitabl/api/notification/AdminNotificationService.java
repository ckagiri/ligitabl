package com.ligitabl.api.notification;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Domain facade for admin-facing operational notifications.
 *
 * Two tiers:
 * - info: Slack only — lifecycle events an admin wants visibility on, not action.
 * - alert: Slack + admin email — events that need admin intervention.
 *
 * Slack is the primary channel; email is the stub {@link EmailService} until a real
 * provider is wired.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminNotificationService {
    private final EmailService emailService;
    private final SlackNotificationService slackNotificationService;

    // --- Match sync / circuit breaker ---

    public void notifyCircuitBreakerOpened(int consecutiveFailures, long recoveryWaitMinutes) {
        alert(
                "Match sync circuit breaker OPEN",
                "Circuit breaker opened after " + consecutiveFailures + " consecutive failures.\n" + "Will retry after "
                        + recoveryWaitMinutes + " minute(s).");
    }

    public void notifyCircuitBreakerRecovered(int previousFailures) {
        info(
                "Match sync circuit breaker RECOVERED",
                "Circuit breaker recovered after " + previousFailures + " failures.");
    }

    public void notifyBlockedFinalization(
            UUID roundId, int roundPosition, List<UUID> blockingMatchIds, List<String> matchDetails) {
        alert(
                "Round finalization BLOCKED",
                "Round finalization blocked.\n" + "Round ID: "
                        + roundId + "\n" + "Round position: "
                        + roundPosition + "\n" + "Blocking matches: "
                        + blockingMatchIds.size() + "\n\n" + String.join("\n", matchDetails));
    }

    public void notifyStartup(String competitionCode) {
        info(
                "Application started",
                "Initial match sync starting on application startup (competition: " + competitionCode + ").");
    }

    public void notifySyncScheduleChanged(UUID roundId, int roundPosition, Duration delay, String reason) {
        info(
                "Match sync cadence changed",
                "Next sync in " + formatDuration(delay) + " — " + reason + "\n" + "Round position: " + roundPosition
                        + "\n" + "Round ID: " + roundId);
    }

    // --- Round finalization / advancement ---

    public void notifyRoundFinalized(UUID roundId, int roundPosition, int submissions, int results, boolean lastRound) {
        info(
                "Round finalized",
                "Round " + roundPosition + " finalized" + (lastRound ? " (last round of season)" : "") + ".\n"
                        + "Submissions: " + submissions + "\n" + "Results: "
                        + results + "\n" + "Round ID: " + roundId);
    }

    public void notifyAdvancementScheduled(
            UUID roundId, int roundPosition, OffsetDateTime advanceAt, int delayMinutes) {
        info(
                "Round advancement scheduled",
                "Round " + roundPosition + " will auto-advance at " + advanceAt + " (in " + delayMinutes
                        + " minute(s)).\n" + "Round ID: " + roundId);
    }

    public void notifyRoundAdvanced(int fromPosition, UUID seasonId, boolean lastRound) {
        info(
                "Round advanced",
                lastRound
                        ? "Last round (position " + fromPosition + ") advanced — season completion is a separate "
                                + "admin action.\n" + "Season ID: " + seasonId
                        : "Advanced from round " + fromPosition + " to " + (fromPosition + 1) + ".\n" + "Season ID: "
                                + seasonId);
    }

    public void notifyAdvancementFailed(UUID roundId, String error) {
        alert(
                "Round advancement FAILED",
                "Automatic round advancement failed.\n" + "Round ID: " + roundId + "\n" + "Error: " + error);
    }

    // --- Matchday / season lifecycle ---

    public void notifyMatchdayAdvanced(int previousMatchday, int newMatchday, UUID seasonId) {
        info(
                "Matchday advanced",
                "Matchday " + previousMatchday + " → " + newMatchday + ".\n" + "Season ID: " + seasonId);
    }

    public void notifySeasonActivated(String competitionSlug, UUID promotedSeasonId, UUID previousSeasonId) {
        info(
                "Season activated",
                "Competition " + competitionSlug + ": upcoming season promoted to active.\n" + "New season ID: "
                        + promotedSeasonId + "\n" + "Previous season ID: " + previousSeasonId);
    }

    // --- Tiers ---

    private void info(String title, String body) {
        log.info("{}: {}", title, body);
        slackNotificationService.send("ℹ️ *" + title + "*\n" + body);
    }

    private void alert(String title, String body) {
        log.warn("{}: {}", title, body);
        slackNotificationService.send("🔴 *" + title + "*\n" + body);
        emailService.sendAdminAlert(title, body);
    }

    private static String formatDuration(Duration duration) {
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.toSeconds() % 60;

        if (hours > 0) {
            return String.format(
                    "%d hour%s %d minute%s", hours, hours == 1 ? "" : "s", minutes, minutes == 1 ? "" : "s");
        }
        if (minutes > 0) {
            return String.format("%d minute%s", minutes, minutes == 1 ? "" : "s");
        }
        return String.format("%d second%s", seconds, seconds == 1 ? "" : "s");
    }
}
