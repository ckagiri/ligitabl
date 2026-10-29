package com.ligitabl.api.logging;

import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;

/** Correlation id for one HTTP request, set by {@code RequestLoggingFilter} and read from MDC. */
public final class RequestId {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    // The id is client-supplied and lands in logs, headers and JSON bodies.
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private RequestId() {}

    public static String resolve(String incoming) {
        return incoming != null && SAFE.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
    }

    /** Null outside a request. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
