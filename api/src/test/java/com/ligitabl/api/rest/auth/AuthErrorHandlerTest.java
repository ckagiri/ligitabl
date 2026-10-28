package com.ligitabl.api.rest.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

import com.ligitabl.api.logging.RequestId;
import com.ligitabl.api.shared.errors.UnexpectedError;
import com.ligitabl.api.shared.errors.UseCaseErrors;
import com.ligitabl.api.shared.exceptions.UseCaseException;

class AuthErrorHandlerTest {

    private final AuthErrorHandler handler = new AuthErrorHandler();

    @AfterEach
    void clearRequestId() {
        MDC.remove(RequestId.MDC_KEY);
    }

    @Test
    void unexpectedErrorReturnsAGenericBodyWithTheRequestId() {
        MDC.put(RequestId.MDC_KEY, "req-1");
        var ex = new UseCaseException(UseCaseErrors.unexpected(new NullPointerException("\"hashed\" is null")));

        var response = handler.handleUseCase(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody())
                .isEqualTo(new AuthDto.ErrorResponse("INTERNAL_ERROR", UnexpectedError.PUBLIC_MESSAGE, null, "req-1"));
    }
}
