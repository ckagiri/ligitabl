package com.ligitabl.api.config;

import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import reactor.netty.http.client.HttpClient;

/**
 * WebClient for Slack incoming-webhook notifications.
 *
 * No retry filter, unlike {@link WebClientConfig}'s football-data client: notifications are
 * fire-and-forget and a dropped message is acceptable; a retry storm against Slack is not.
 * No baseUrl — the full webhook URL is supplied per-request by SlackNotificationService.
 */
@Configuration
public class SlackConfig {

    @Value("${ligitabl.slack.timeout-seconds:5}")
    private int timeoutSeconds;

    @Bean
    public WebClient slackWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutSeconds * 1000)
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeoutSeconds, TimeUnit.SECONDS)));

        @SuppressWarnings("null")
        ReactorClientHttpConnector connector = new ReactorClientHttpConnector(httpClient);

        return WebClient.builder().clientConnector(connector).build();
    }
}
