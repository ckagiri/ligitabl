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
 * Deliberately has no retry: this runs in the critical path of an interactive registration
 * request, so any failure (network error, timeout, or a success:false response) is surfaced as a
 * single Either.left immediately, leaving the fail-closed decision to the caller.
 */
@Service
@Slf4j
public class TurnstileClient {

    private final WebClient webClient;
    private final String secretKey;
    private final boolean enabled;

    public TurnstileClient(
            @Qualifier("turnstileWebClient") WebClient webClient,
            @Value("${ligitabl.turnstile.secret-key}") String secretKey,
            @Value("${ligitabl.turnstile.enabled:true}") boolean enabled) {
        this.webClient = webClient;
        this.secretKey = secretKey;
        this.enabled = enabled;
    }

    /**
     * Operational kill-switch (ligitabl.turnstile.enabled) so a prolonged Cloudflare outage can be
     * worked around via one env var, without a deploy. Callers should skip both the token-presence
     * check and {@link #verify} entirely when this is false, not just tolerate a bypassed verify.
     */
    public boolean isEnabled() {
        return enabled;
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
