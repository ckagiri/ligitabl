package com.ligitabl.api.scheduling.health;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ligitabl.api.notification.AdminNotificationService;

import io.sentry.Sentry;
import io.sentry.SentryLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scans {@link JobHeartbeat} for jobs that have gone quiet and alerts an admin. The detection half
 * of the 2026-09-06 incident response (see {@code .art/task_89.md}).
 *
 * <p>⚠️ <strong>Must never implement {@code HealthIndicator}.</strong> Such beans are aggregated
 * into {@code /actuator/health}, which the container healthcheck polls — so a stalled background
 * job would restart-loop a container that is otherwise serving users fine. Alerting is the whole
 * job; recovery stays a human decision.
 */
@Component
@ConditionalOnProperty(
        name = {"ligitabl.scheduling.enabled", "ligitabl.scheduling.watchdog.enabled"},
        havingValue = "true",
        matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class JobWatchdog {

    private final JobHeartbeat heartbeat;
    private final AdminNotificationService adminNotificationService;
    private final Clock clock;

    @Value("${ligitabl.scheduling.watchdog.alert-cooldown-minutes:360}")
    private long alertCooldownMinutes;

    /** Initial delay must outlast the slowest job's own, or a job yet to tick reads as stalled. */
    @Scheduled(
            fixedDelayString = "${ligitabl.scheduling.watchdog.interval-ms:300000}",
            initialDelayString = "${ligitabl.scheduling.watchdog.initial-delay-ms:600000}")
    public void checkHeartbeats() {
        try {
            Instant now = Instant.now(clock);
            var entries = heartbeat.snapshot();

            if (entries.isEmpty()) {
                log.info("[JOB_HEARTBEAT_SUMMARY] no jobs registered");
                return;
            }

            int stale = 0;
            var summary = new StringBuilder();

            for (var entry : entries) {
                boolean isStale = heartbeat.isStale(entry, now);
                Duration since = heartbeat.timeSinceHealthy(entry, now);

                if (summary.length() > 0) {
                    summary.append(", ");
                }
                summary.append(entry.jobName())
                        .append('=')
                        .append(isStale ? "STALE" : "ok")
                        .append('(')
                        .append(since.toMinutes())
                        .append("m, n=")
                        .append(entry.pingCount())
                        .append(')');

                if (isStale) {
                    stale++;
                    maybeAlert(entry, since, now);
                }
            }

            // INFO so it ships to Logtail. Its absence is the backstop signal — the only one that
            // survives the JVM dying. See docs/background-jobs.md for the alert rule.
            log.info("[JOB_HEARTBEAT_SUMMARY] stale={}/{} {}", stale, entries.size(), summary);

        } catch (Exception e) {
            log.error("[JOB_WATCHDOG_FAILED] {}", e.getMessage(), e);
            Sentry.captureException(e);
        }
    }

    private void maybeAlert(JobHeartbeat.Entry entry, Duration since, Instant now) {
        Duration cooldown = Duration.ofMinutes(alertCooldownMinutes);
        Instant lastAlertAt = entry.lastAlertAt();

        if (lastAlertAt != null && Duration.between(lastAlertAt, now).compareTo(cooldown) < 0) {
            log.debug(
                    "[JOB_STALLED_SUPPRESSED] job={} alerted {} ago, within {} cooldown",
                    entry.jobName(),
                    Duration.between(lastAlertAt, now),
                    cooldown);
            return;
        }

        log.error(
                "[JOB_STALLED] job={} lastHealthyAt={} since={} maxInterval={} pings={}",
                entry.jobName(),
                entry.lastHealthyAt(),
                since,
                entry.maxInterval(),
                entry.pingCount());

        // captureMessage, not an exception: there is no throwable, and a message groups better
        // than a synthetic stack. Explicit because additivity="false" keeps log.error out of Sentry.
        Sentry.captureMessage(
                "Background job stalled: " + entry.jobName() + " (no success for " + since + ")", SentryLevel.ERROR);

        adminNotificationService.notifyJobStalled(
                entry.jobName(), entry.lastHealthyAt(), since, entry.maxInterval(), entry.pingCount());

        heartbeat.markAlerted(entry.jobName(), now);
    }
}
