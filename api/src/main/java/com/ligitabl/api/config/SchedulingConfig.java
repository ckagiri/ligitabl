package com.ligitabl.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Scheduling Configuration
 *
 * Provides TaskScheduler for dynamic scheduling of jobs.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("scheduled-task-");
        // Important: these jobs are scheduled into the future; waiting for "all tasks" to complete
        // can block shutdown indefinitely (and causes Maven Surefire to time out in tests).
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setAwaitTerminationSeconds(5);
        scheduler.setErrorHandler(t -> {
            // Log errors but don't stop scheduler
            System.err.println("Error in scheduled task: " + t.getMessage());
            t.printStackTrace();
        });
        scheduler.initialize();
        return scheduler;
    }
}
