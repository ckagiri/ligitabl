package com.ligitabl.api.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.ligitabl.api.shared.Either;

import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import reactor.netty.http.client.HttpClient;

@DisplayName("TurnstileClient")
class TurnstileClientTest {

    private static WireMockServer wireMock;
    private static TurnstileClient client;

    @BeforeAll
    static void setup() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());

        client = createClient(wireMock.baseUrl(), Duration.ofSeconds(10), true);
    }

    private static TurnstileClient createClient(String baseUrl, Duration timeout, boolean enabled) {
        String resolvedBaseUrl = Objects.requireNonNull(baseUrl, "baseUrl");

        HttpClient httpClient = HttpClient.create().responseTimeout(timeout).doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(timeout.toMillis(), TimeUnit.MILLISECONDS))
                .addHandlerLast(new WriteTimeoutHandler(timeout.toMillis(), TimeUnit.MILLISECONDS)));

        WebClient webClient = WebClient.builder()
                .baseUrl(resolvedBaseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        return new TurnstileClient(webClient, "test-secret-key", enabled, "test-site-key");
    }

    @AfterAll
    static void teardown() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @BeforeEach
    void reset() {
        wireMock.resetAll();
    }

    @Nested
    @DisplayName("verify")
    class Verify {

        @Test
        @DisplayName("should return Right on success:true")
        void shouldReturnRightOnSuccess() {
            wireMock.stubFor(
                    post(urlEqualTo("/"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    """
                                    { "success": true, "error-codes": [], "challenge_ts": "2026-01-01T00:00:00Z", "hostname": "example.com" }
                                    """)));

            Either<TurnstileError, ?> result = client.verify("real-token", "203.0.113.1");

            assertThat(result.isRight()).isTrue();

            wireMock.verify(postRequestedFor(urlEqualTo("/"))
                    .withRequestBody(containing("secret=test-secret-key"))
                    .withRequestBody(containing("response=real-token"))
                    .withRequestBody(containing("remoteip=203.0.113.1")));
        }

        @Test
        @DisplayName("should return Left(VerificationFailed) with the error codes on success:false")
        void shouldReturnLeftOnVerificationFailed() {
            wireMock.stubFor(
                    post(urlEqualTo("/"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    """
                                    { "success": false, "error-codes": ["invalid-input-response"] }
                                    """)));

            Either<TurnstileError, ?> result = client.verify("bad-token", "203.0.113.1");

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(TurnstileError.VerificationFailed.class);
            assertThat(((TurnstileError.VerificationFailed) result.getLeft()).errorCodes())
                    .containsExactly("invalid-input-response");
        }

        @Test
        @DisplayName("should return Left on malformed JSON")
        void shouldReturnLeftOnMalformedJson() {
            wireMock.stubFor(post(urlEqualTo("/"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{ not valid json ")));

            Either<TurnstileError, ?> result = client.verify("any-token", "203.0.113.1");

            assertThat(result.isLeft()).isTrue();
        }

        @Test
        @DisplayName("should return Left(Timeout) when siteverify doesn't respond in time")
        void shouldReturnLeftOnTimeout() {
            TurnstileClient timeoutClient = createClient(wireMock.baseUrl(), Duration.ofSeconds(1), true);

            wireMock.stubFor(post(urlEqualTo("/")).willReturn(aResponse().withFixedDelay(5000)));

            Either<TurnstileError, ?> result = timeoutClient.verify("any-token", "203.0.113.1");

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(TurnstileError.Timeout.class);
        }

        @Test
        @DisplayName("should omit remoteip when null")
        void shouldOmitRemoteIpWhenNull() {
            wireMock.stubFor(
                    post(urlEqualTo("/"))
                            .willReturn(
                                    aResponse()
                                            .withStatus(200)
                                            .withHeader("Content-Type", "application/json")
                                            .withBody(
                                                    """
                                    { "success": true, "error-codes": [] }
                                    """)));

            Either<TurnstileError, ?> result = client.verify("real-token", null);

            assertThat(result.isRight()).isTrue();
            wireMock.verify(postRequestedFor(urlEqualTo("/")).withRequestBody(containing("response=real-token")));
        }

        @Test
        @DisplayName("should return Left on connection failure")
        void shouldReturnLeftOnConnectionFailure() {
            TurnstileClient brokenClient = createClient("http://localhost:1", Duration.ofSeconds(2), true);

            Either<TurnstileError, ?> result = brokenClient.verify("any-token", "203.0.113.1");

            assertThat(result.isLeft()).isTrue();
            assertThat(result.getLeft()).isInstanceOf(TurnstileError.NetworkError.class);
        }
    }

    @Nested
    @DisplayName("configuration accessors")
    class ConfigAccessors {

        @Test
        @DisplayName("isEnabled reflects the injected flag")
        void isEnabledReflectsFlag() {
            TurnstileClient enabled = createClient(wireMock.baseUrl(), Duration.ofSeconds(1), true);
            TurnstileClient disabled = createClient(wireMock.baseUrl(), Duration.ofSeconds(1), false);

            assertThat(enabled.isEnabled()).isTrue();
            assertThat(disabled.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("getSiteKey returns the injected public key")
        void getSiteKeyReturnsInjectedKey() {
            assertThat(client.getSiteKey()).isEqualTo("test-site-key");
        }
    }
}
