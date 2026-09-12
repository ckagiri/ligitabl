package com.ligitabl.api.scheduling.health;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Records when each monitored background job last succeeded, so {@link JobWatchdog} can detect a
 * job that has gone <em>quiet</em> rather than one that failed loudly. Every other signal in the
 * codebase fires on failure; nothing fired when the match-sync chain stopped being scheduled on
 * 2026-09-06 (see {@code .art/task_89.md}).
 *
 * <p>Jobs {@link #register} from {@code @PostConstruct} and {@link #ping} on success. Registering
 * at bean creation is what distinguishes two cases: a job disabled by
 * {@code @ConditionalOnProperty} never constructs, so is never alerted on; a job that exists but
 * whose schedule is broken registers and never pings, so staleness measures from
 * {@code registeredAt} and still alerts.
 *
 * <p>Mutations go through {@link ConcurrentHashMap#compute}, never {@code put}, so a concurrent
 * {@link #markAlerted} cannot be lost.
 */
@Component
@Slf4j
public class JobHeartbeat {

    /** Alerting at exactly the declared interval would fire on any run overlapping its next tick. */
    private static final double GRACE_FACTOR = 1.5;

    /** Floor on the grace: 1.5x of a fast job's interval false-fires on a GC pause. */
    private static final Duration MIN_GRACE = Duration.ofMinutes(5);

    private static final Duration UNREGISTERED_FALLBACK_INTERVAL = Duration.ofHours(24);

    /**
     * @param lastPingAt null until the first success; staleness then measures from
     *     {@code registeredAt}
     * @param lastAlertAt drives the watchdog's alert cooldown
     */
    public record Entry(
            String jobName,
            Duration maxInterval,
            Instant registeredAt,
            Instant lastPingAt,
            long pingCount,
            Instant lastAlertAt) {

        Entry withPing(Instant at) {
            // Clearing lastAlertAt lets a job that recovers then breaks again alert promptly.
            return new Entry(jobName, maxInterval, registeredAt, at, pingCount + 1, null);
        }

        Entry withAlert(Instant at) {
            return new Entry(jobName, maxInterval, registeredAt, lastPingAt, pingCount, at);
        }

        /** The last point at which this job is known to have been healthy. */
        public Instant lastHealthyAt() {
            return lastPingAt != null ? lastPingAt : registeredAt;
        }
    }

    private final Clock clock;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public JobHeartbeat(Clock clock) {
        this.clock = clock;
    }

    /**
     * Idempotent; re-registering keeps any ping history.
     *
     * @param maxInterval the longest <em>legitimate</em> gap, not the nominal schedule — a job
     *     polling every 6h but legitimately backing off to 24h declares 24h
     */
    public void register(String jobName, Duration maxInterval) {
        Instant now = Instant.now(clock);
        entries.compute(
                jobName,
                (name, existing) -> existing == null
                        ? new Entry(name, maxInterval, now, null, 0L, null)
                        : new Entry(
                                name,
                                maxInterval,
                                existing.registeredAt(),
                                existing.lastPingAt(),
                                existing.pingCount(),
                                existing.lastAlertAt()));
        log.info("[JOB_HEARTBEAT_REGISTERED] job={} maxInterval={}", jobName, maxInterval);
    }

    /**
     * An unregistered name is registered defensively rather than rejected: a typo in a job name
     * must not break the job itself.
     */
    public void ping(String jobName) {
        Instant now = Instant.now(clock);
        entries.compute(jobName, (name, existing) -> {
            if (existing == null) {
                log.warn(
                        "[JOB_HEARTBEAT_UNREGISTERED] job={} pinged without registering; "
                                + "tracking with a {} fallback interval",
                        name,
                        UNREGISTERED_FALLBACK_INTERVAL);
                return new Entry(name, UNREGISTERED_FALLBACK_INTERVAL, now, now, 1L, null);
            }
            return existing.withPing(now);
        });
    }

    /** Snapshot of every registered job, ordered by name so logs are stable between runs. */
    public List<Entry> snapshot() {
        return entries.values().stream()
                .sorted(Comparator.comparing(e -> e.jobName()))
                .toList();
    }

    /** Arms the per-job alert cooldown. Package-private: only {@link JobWatchdog} should call it. */
    void markAlerted(String jobName, Instant at) {
        entries.computeIfPresent(jobName, (name, existing) -> existing.withAlert(at));
    }

    /** True when this job's last healthy point is further back than its interval plus grace. */
    public boolean isStale(Entry entry, Instant now) {
        return now.isAfter(entry.lastHealthyAt().plus(graceFor(entry.maxInterval())));
    }

    /** How long this job has been unaccounted for, measured from its last healthy point. */
    public Duration timeSinceHealthy(Entry entry, Instant now) {
        return Duration.between(entry.lastHealthyAt(), now);
    }

    /** The declared interval plus grace: {@code max(interval * 1.5, interval + 5min)}. */
    static Duration graceFor(Duration maxInterval) {
        Duration scaled = Duration.ofMillis(Math.round(maxInterval.toMillis() * GRACE_FACTOR));
        Duration floored = maxInterval.plus(MIN_GRACE);
        return scaled.compareTo(floored) >= 0 ? scaled : floored;
    }
}
