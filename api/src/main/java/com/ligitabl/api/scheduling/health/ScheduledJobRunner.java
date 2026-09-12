package com.ligitabl.api.scheduling.health;

import org.springframework.stereotype.Component;

import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Runs a scheduled job's body with uniform failure handling, optionally pinging its heartbeat. The
 * try/catch + log + Sentry trio was copy-pasted across seven jobs and the copies had drifted —
 * some omitted Sentry, some the try/catch entirely.
 *
 * <p>Sentry is captured explicitly because {@code additivity="false"} on
 * {@code com.ligitabl.api.scheduling} (logback-spring.xml) keeps {@code log.error} out of it.
 *
 * <p>⚠️ Never wrap a {@code @Transactional} method of the calling class: the lambda is a
 * self-invocation that bypasses the proxy and runs without a transaction. Such a job hand-writes
 * its try/catch instead — see {@code SeasonActivationService}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledJobRunner {

    private final JobHeartbeat heartbeat;

    /** For jobs whose silence should be alerted on. */
    public void runSafely(String jobName, Runnable body) {
        run(jobName, body, true);
    }

    /**
     * For jobs that are guarded but deliberately unmonitored — where another job's heartbeat
     * already proves the bean and pool are alive. See {@code docs/background-jobs.md}.
     */
    public void runGuarded(String jobName, Runnable body) {
        run(jobName, body, false);
    }

    private void run(String jobName, Runnable body, boolean ping) {
        try {
            body.run();
            if (ping) {
                heartbeat.ping(jobName);
            }
        } catch (Exception e) {
            // Not rethrown: the pool ErrorHandler is for gaps in guarding, and expected failures
            // arriving there would make real gaps harder to spot.
            log.error("[JOB_FAILED] job={}: {}", jobName, e.getMessage(), e);
            Sentry.captureException(e);
        }
    }
}
