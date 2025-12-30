package com.ligitabl.api.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.lang.NonNull;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;

/**
 * WebClient Configuration for Football Data API
 *
 * Features:
 * - Automatic retry with exponential backoff (3 attempts)
 * - Connection and read timeouts
 * - Request/response logging
 * - Error handling
 */
@Configuration
public class WebClientConfig {

    private static final Logger log = LoggerFactory.getLogger(WebClientConfig.class);

    @Value("${football-data.api.url:https://api.football-data.org/v4}")
    private @NonNull String baseUrl = "";

    @Value("${football-data.api.token:}")
    private @NonNull String apiToken = "";

    @Value("${football-data.api.timeout-seconds:10}")
    private int timeoutSeconds;

    @Value("${football-data.api.retry-attempts:3}")
    private int retryAttempts;

    @Bean
    public WebClient footballDataWebClient() {
        // Configure HTTP client with timeouts
        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutSeconds * 1000)
            .doOnConnected(conn -> conn
                .addHandlerLast(new ReadTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS))
                .addHandlerLast(new WriteTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS))
            );

        @SuppressWarnings("null")
        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Auth-Token", apiToken)
                .defaultHeader("Accept", "application/json")
                .clientConnector(connector)
                .filter(logRequest())
                .filter(logResponse())
                .filter(retryFilter())
                .build();
    }

    /**
     * Retry filter with exponential backoff.
     * Retries on:
     * - Network errors (5xx)
     * - Timeout errors
     *
     * Does NOT retry on:
     * - Client errors (4xx) except 429 (rate limit)
     */
    private @NonNull ExchangeFilterFunction retryFilter() {
        return (request, next) -> next.exchange(request)
                .flatMap(response -> {
                    // Retry on 5xx or 429 (rate limit)
                    if (response.statusCode().is5xxServerError() ||
                            response.statusCode().value() == 429) {
                        return Mono.error(new RuntimeException(
                                "Server error or rate limit: " + response.statusCode()
                        ));
                    }
                    return Mono.just(response);
                })
                .retryWhen(Retry.backoff(retryAttempts, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(10))
                        .doBeforeRetry(signal ->
                                log.warn("Retrying Football Data API request, attempt: {}/{}, error: {}",
                                        signal.totalRetries() + 1,
                                        retryAttempts,
                                        signal.failure().getMessage())
                        )
                );
    }

    /**
     * Log outgoing requests
     */
    private @NonNull ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.debug("Football Data API Request: {} {}",
                    request.method(),
                    request.url()
            );
            return Mono.just(request);
        });
    }

    /**
     * Log incoming responses
     */
    private @NonNull ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            log.debug("Football Data API Response: {} ({})",
                    response.statusCode(),
                    response.headers().asHttpHeaders().getFirst("X-Requests-Available")
            );
            return Mono.just(response);
        });
    }
}
