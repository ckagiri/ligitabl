package com.ligitabl.api.shared.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

import com.ligitabl.api.shared.errors.*;

class UseCaseErrorStatusTest {

    static Stream<Arguments> everyVariant() {
        return Stream.of(
                Arguments.of(new ValidationError("bad"), HttpStatus.BAD_REQUEST),
                Arguments.of(new AuthenticationError("who"), HttpStatus.UNAUTHORIZED),
                Arguments.of(new AuthorizationError("no"), HttpStatus.FORBIDDEN),
                Arguments.of(new NotFoundError("gone"), HttpStatus.NOT_FOUND),
                Arguments.of(new ConflictError("clash"), HttpStatus.CONFLICT),
                Arguments.of(new UnexpectedError(new RuntimeException("boom")), HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @ParameterizedTest
    @MethodSource("everyVariant")
    void mapsEachVariantToItsStatus(UseCaseError error, HttpStatus expected) {
        assertThat(UseCaseErrorStatus.of(error)).isEqualTo(expected);
    }
}
