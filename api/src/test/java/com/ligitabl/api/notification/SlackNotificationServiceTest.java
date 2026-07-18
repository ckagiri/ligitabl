package com.ligitabl.api.notification;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import reactor.netty.http.client.HttpClient;

@DisplayName("SlackNotificationService")
class SlackNotificationServiceTest {

    private static final String WEBHOOK_PATH = "/services/T000/B000/secret";
    private static final String NEW_USER_WEBHOOK_PATH = "/services/T000/B111/secret";

    private static WireMockServer wireMock;

    @BeforeAll
    static void setup() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());
    }

    @AfterAll
    static void teardown() {
        wireMock.stop();
    }

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
        wireMock.stubFor(post(urlEqualTo(WEBHOOK_PATH)).willReturn(aResponse().withStatus(200)));
        wireMock.stubFor(post(urlEqualTo(NEW_USER_WEBHOOK_PATH)).willReturn(aResponse().withStatus(200)));
    }

    private static SlackNotificationService createService(String webhookUrl, boolean enabled) {
        return createService(webhookUrl, "", enabled);
    }

    private static SlackNotificationService createService(
            String webhookUrl, String newUserWebhookUrl, boolean enabled) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(5))
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(5, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(5, TimeUnit.SECONDS)));

        WebClient webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        return new SlackNotificationService(webClient, webhookUrl, newUserWebhookUrl, enabled, 5);
    }

    @Test
    void newUsersChannel_postsToNewUserWebhook_notDefault() throws Exception {
        createService(wireMock.baseUrl() + WEBHOOK_PATH, wireMock.baseUrl() + NEW_USER_WEBHOOK_PATH, true)
                .send(SlackNotificationService.Channel.NEW_USERS, "New user signup");

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> wireMock.verify(postRequestedFor(urlEqualTo(NEW_USER_WEBHOOK_PATH))
                        .withRequestBody(equalToJson("{\"text\":\"New user signup\"}"))));
        wireMock.verify(0, postRequestedFor(urlEqualTo(WEBHOOK_PATH)));
    }

    @Test
    void newUsersChannel_blankNewUserUrl_dropsWithoutFallingBackToDefault() throws Exception {
        createService(wireMock.baseUrl() + WEBHOOK_PATH, "", true)
                .send(SlackNotificationService.Channel.NEW_USERS, "dropped");

        Thread.sleep(200);
        wireMock.verify(0, postRequestedFor(urlEqualTo(WEBHOOK_PATH)));
        wireMock.verify(0, postRequestedFor(urlEqualTo(NEW_USER_WEBHOOK_PATH)));
    }

    @Test
    void postsTextPayloadToWebhook() {
        createService(wireMock.baseUrl() + WEBHOOK_PATH, true).send("Round finalized");

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> wireMock.verify(postRequestedFor(urlEqualTo(WEBHOOK_PATH))
                        .withRequestBody(equalToJson("{\"text\":\"Round finalized\"}"))));
    }

    @Test
    void escapesQuotesAndNewlinesAsValidJson() {
        createService(wireMock.baseUrl() + WEBHOOK_PATH, true).send("line \"one\"\nline two");

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> wireMock.verify(postRequestedFor(urlEqualTo(WEBHOOK_PATH))
                        .withRequestBody(equalToJson("{\"text\":\"line \\\"one\\\"\\nline two\"}"))));
    }

    @Test
    void disabled_sendsNothing() throws Exception {
        createService(wireMock.baseUrl() + WEBHOOK_PATH, false).send("dropped");

        Thread.sleep(200);
        wireMock.verify(0, postRequestedFor(urlEqualTo(WEBHOOK_PATH)));
    }

    @Test
    void enabledButBlankUrl_sendsNothingAndDoesNotThrow() throws Exception {
        assertThatCode(() -> createService("", true).send("dropped")).doesNotThrowAnyException();

        Thread.sleep(200);
        wireMock.verify(0, postRequestedFor(urlEqualTo(WEBHOOK_PATH)));
    }

    @Test
    void webhookServerError_doesNotThrow() {
        wireMock.stubFor(post(urlEqualTo(WEBHOOK_PATH)).willReturn(aResponse().withStatus(500)));

        assertThatCode(() ->
                        createService(wireMock.baseUrl() + WEBHOOK_PATH, true).send("boom"))
                .doesNotThrowAnyException();

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> wireMock.verify(postRequestedFor(urlEqualTo(WEBHOOK_PATH))));
    }

    @Test
    void unreachableWebhook_doesNotThrow() {
        // Port from the ephemeral range with nothing listening — connection refused.
        assertThatCode(() ->
                        createService("http://localhost:1/unreachable", true).send("boom"))
                .doesNotThrowAnyException();
    }
}
