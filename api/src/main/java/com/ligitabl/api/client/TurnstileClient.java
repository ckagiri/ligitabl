package com.ligitabl.api.client;

import java.util.List;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.ligitabl.api.client.turnstile.TurnstileVerifyResponse;
import com.ligitabl.api.shared.Either;

import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.WriteTimeoutException;
import lombok.extern.slf4j.Slf4j;

/**
 * Client for Cloudflare Turnstile's siteverify endpoint.
 *
 * No retry: this sits in an interactive registration request, so any failure (network error,
 * timeout, or success:false) is one immediate Either.left, and the caller decides fail-closed.
 */
@Service
@Slf4j
public class TurnstileClient {

    private final WebClient webClient;
    private final String secretKey;
    private final boolean enabled;
    private final String siteKey;

    public TurnstileClient(
            @Qualifier("turnstileWebClient") WebClient webClient,
            @Value("${ligitabl.turnstile.secret-key}") String secretKey,
            @Value("${ligitabl.turnstile.enabled:true}") boolean enabled,
            @Value("${ligitabl.turnstile.site-key}") String siteKey) {
        this.webClient = webClient;
        this.secretKey = secretKey;
        this.enabled = enabled;
        this.siteKey = siteKey;
    }

    /**
     * Kill-switch ({@code ligitabl.turnstile.enabled}) for riding out a Cloudflare outage without a
     * deploy. When false, callers skip both the token-presence check and {@link #verify}.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Public site key for the widget's data-sitekey attribute. Here rather than a {@code @Value} field
     * on AuthController, which uses {@code @RequiredArgsConstructor}: {@code @Value} goes through an
     * explicit constructor in this codebase.
     */
    public String getSiteKey() {
        return siteKey;
    }

    public Either<TurnstileError, TurnstileVerifyResponse> verify(String token, String remoteIp) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("secret", secretKey);
        formData.add("response", token);
        if (remoteIp != null && !remoteIp.isBlank()) {
            formData.add("remoteip", remoteIp);
        }

        try {
            TurnstileVerifyResponse response = webClient
                    .post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(TurnstileVerifyResponse.class)
                    .block();

            if (response == null) {
                return Either.left(new TurnstileError.UnexpectedError("Null response from Turnstile siteverify"));
            }

            if (!response.success()) {
                List<String> errorCodes = response.errorCodes() == null ? List.of() : response.errorCodes();
                // hostname must match the widget's allowed list — logging it separates a domain
                // mismatch from an expired token, which share the invalid-input-response code.
                log.warn(
                        "[TURNSTILE_VERIFY_REJECTED] errorCodes={} hostname={} challengeTs={}",
                        errorCodes,
                        response.hostname(),
                        response.challengeTs());
                return Either.left(new TurnstileError.VerificationFailed(errorCodes));
            }

            return Either.right(response);

        } catch (Exception e) {
            return handleException(e);
        }
    }

    private Either<TurnstileError, TurnstileVerifyResponse> handleException(Exception e) {
        if (isTimeout(e)) {
            log.warn("[TURNSTILE_VERIFY_TIMEOUT] {}", e.getMessage());
            return Either.left(new TurnstileError.Timeout("Turnstile siteverify timed out"));
        }
        log.warn("[TURNSTILE_VERIFY_NETWORK_ERROR] {}", e.getMessage());
        return Either.left(new TurnstileError.NetworkError("Turnstile siteverify call failed: " + e.getMessage(), e));
    }

    private static boolean isTimeout(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof TimeoutException
                    || cur instanceof ReadTimeoutException
                    || cur instanceof WriteTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
