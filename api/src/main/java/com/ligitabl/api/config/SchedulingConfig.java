package com.ligitabl.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import io.sentry.Sentry;

/**
 * Scheduling Configuration
 *
 * Provides TaskScheduler for dynamic scheduling of jobs.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    private static final Logger log = LoggerFactory.getLogger(SchedulingConfig.class);

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        // Shared by every @Scheduled job plus the MatchSyncScheduler chain and JobWatchdog.
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("scheduled-task-");
        // Important: these jobs are scheduled into the future; waiting for "all tasks" to complete
        // can block shutdown indefinitely (and causes Maven Surefire to time out in tests).
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setAwaitTerminationSeconds(5);
        scheduler.setErrorHandler(t -> {
            // Was System.err/printStackTrace, which reached neither Logtail nor Sentry — the blind
            // spot behind the five-day stall in .art/task_89.md.
            try {
                log.error("[SCHEDULED_TASK_UNCAUGHT] Unhandled error escaped a scheduled task", t);
                Sentry.captureException(t);
            } catch (Throwable ignored) {
                // Throwing here would kill the pool thread this exists to report on.
            }
        });
        scheduler.initialize();
        return scheduler;
    }
}
