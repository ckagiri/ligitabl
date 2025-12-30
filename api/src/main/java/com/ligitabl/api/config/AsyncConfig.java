package com.ligitabl.api.config;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Async Processing Configuration
 *
 * Configures thread pools for asynchronous operations.
 * Currently used for standings recalculation.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * Thread pool for async standings recalculation
     */
    @Bean(name = "asyncStandingsExecutor")
    public Executor asyncStandingsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core pool size: number of threads to keep alive
        executor.setCorePoolSize(2);

        // Max pool size: maximum number of threads
        executor.setMaxPoolSize(5);

        // Queue capacity: tasks to queue before creating new threads
        executor.setQueueCapacity(100);

        // Thread name prefix for easy identification in logs
        executor.setThreadNamePrefix("async-standings-");

        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // Maximum time to wait for tasks on shutdown (seconds)
        executor.setAwaitTerminationSeconds(60);

        // Rejection policy: log and discard if queue is full
        executor.setRejectedExecutionHandler((runnable, exec) -> {
            log.error("Async standings task rejected - queue full");
        });

        executor.initialize();

        log.info(
                "Async standings executor initialized: corePoolSize={}, maxPoolSize={}, queueCapacity={}",
                executor.getCorePoolSize(),
                executor.getMaxPoolSize(),
                executor.getQueueCapacity());

        return executor;
    }
}
