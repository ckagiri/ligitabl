package com.ligitabl.api.shared.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import com.ligitabl.api.logging.RequestId;
import com.ligitabl.api.shared.errors.UnexpectedError;
import com.ligitabl.api.shared.errors.UseCaseErrors;

class GlobalExceptionHandlerTest {

    private static final String LEAKY = "Cannot invoke \"Password$Hashed.value()\" because \"hashed\" is null";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest("POST", "/api/x"));

    @BeforeEach
    void setRequestId() {
        MDC.put(RequestId.MDC_KEY, "req-1");
    }

    @AfterEach
    void clearRequestId() {
        MDC.remove(RequestId.MDC_KEY);
    }

    @Test
    void unhandledExceptionReturnsAGenericBodyWithTheRequestId() {
        var response = handler.handleUnexpected(new NullPointerException(LEAKY), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo(UnexpectedError.PUBLIC_MESSAGE);
        assertThat(response.getBody().getRequestId()).isEqualTo("req-1");
    }

    @Test
    void unexpectedUseCaseErrorReturnsAGenericBody() {
        var ex = new UseCaseException(UseCaseErrors.unexpected(new IllegalStateException(LEAKY)));

        var response = handler.handleBusinessFailure(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getMessage()).isEqualTo(UnexpectedError.PUBLIC_MESSAGE);
    }

    @Test
    void expectedErrorsKeepTheirMessage() {
        var ex = new UseCaseException(UseCaseErrors.unauthorized("Invalid credentials"));

        var response = handler.handleBusinessFailure(ex, request);

        assertThat(response.getBody().getMessage()).isEqualTo("Invalid credentials");
        assertThat(response.getBody().getRequestId()).isEqualTo("req-1");
    }
}
