package com.ligitabl.api.scheduling.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sync Lock Service
 *
 * Prevents concurrent sync operations from running simultaneously.
 * Includes stuck lock detection and forced release.
 *
 * This is an application-level lock suitable for single-instance deployments.
 * For multi-instance deployments, use database-level locking.
 */
@Component
public class SyncLockService {

    private static final Logger log = LoggerFactory.getLogger(SyncLockService.class);

    private final AtomicBoolean syncInProgress = new AtomicBoolean(false);
    private volatile Instant syncStartedAt = null;

    /**
     * Maximum time a sync should take.
     * If exceeded, assume the lock is stuck and force release.
     */
    private static final Duration MAX_SYNC_DURATION = Duration.ofMinutes(5);

    /**
     * Try to acquire the sync lock
     *
     * @return true if lock acquired, false if sync already in progress
     */
    public boolean acquireLock() {
        // Check if previous sync is stuck
        if (syncInProgress.get() && syncStartedAt != null) {
            Duration elapsed = Duration.between(syncStartedAt, Instant.now());

            if (elapsed.compareTo(MAX_SYNC_DURATION) > 0) {
                log.error("Sync lock appears stuck (elapsed: {} minutes), forcibly releasing",
                        elapsed.toMinutes());
                forceRelease();
            }
        }

        boolean acquired = syncInProgress.compareAndSet(false, true);

        if (acquired) {
            syncStartedAt = Instant.now();
            log.debug("Sync lock acquired at {}", syncStartedAt);
        } else {
            log.warn("Sync already in progress (started: {}), skipping",
                    syncStartedAt);
        }

        return acquired;
    }

    /**
     * Release the sync lock
     */
    public void releaseLock() {
        if (syncInProgress.get()) {
            Duration duration = syncStartedAt != null
                    ? Duration.between(syncStartedAt, Instant.now())
                    : Duration.ZERO;

            log.debug("Sync lock released (duration: {} seconds)",
                    duration.getSeconds());
        }

        syncInProgress.set(false);
        syncStartedAt = null;
    }

    /**
     * Force release the lock (admin override or stuck detection)
     */
    public void forceRelease() {
        log.warn("Forcing sync lock release");
        syncInProgress.set(false);
        syncStartedAt = null;
    }

    /**
     * Check if sync is currently in progress
     */
    public boolean isSyncInProgress() {
        return syncInProgress.get();
    }

    /**
     * Get when current sync started (null if not in progress)
     */
    public Instant getSyncStartedAt() {
        return syncStartedAt;
    }

    /**
     * Get duration of current sync (null if not in progress)
     */
    public Duration getCurrentSyncDuration() {
        if (syncStartedAt == null) {
            return null;
        }
        return Duration.between(syncStartedAt, Instant.now());
    }
}
