package com.ligitabl.api.config;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.lang.NonNull;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import reactor.netty.http.client.HttpClient;

/**
 * WebClient for Cloudflare Turnstile's siteverify endpoint.
 *
 * No retry filter, unlike {@link WebClientConfig}'s football-data client: this call sits in the
 * critical path of an interactive signup request, so a single failed/timed-out attempt goes
 * straight to fail-closed (see TurnstileClient) rather than retrying and blowing the perceived
 * latency budget.
 */
@Configuration
public class TurnstileConfig {

    @Value("${ligitabl.turnstile.verify-url:https://challenges.cloudflare.com/turnstile/v0/siteverify}")
    private @NonNull String verifyUrl = "";

    @Value("${ligitabl.turnstile.timeout-seconds:5}")
    private int timeoutSeconds;

    @Bean
    public WebClient turnstileWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutSeconds * 1000)
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS)));

        @SuppressWarnings("null")
        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);

        return WebClient.builder().baseUrl(verifyUrl).clientConnector(connector).build();
    }
}
