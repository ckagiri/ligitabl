package com.ligitabl.api.config;

import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncStandingsConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncStandingsConfig.class);

    /**
     * Thread pool for async standings recalculation
     */
    @Bean(name = "asyncStandingsExecutor")
    public Executor asyncStandingsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);

        executor.setMaxPoolSize(5);

        executor.setQueueCapacity(100);

        executor.setThreadNamePrefix("async-standings-");

        executor.setWaitForTasksToCompleteOnShutdown(true);

        // Seconds
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
