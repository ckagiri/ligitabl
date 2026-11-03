package com.ligitabl.api.shared.errors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.ligitabl.model.shared.InvalidValueException;
import com.ligitabl.model.shared.RuleViolationException;

class UseCaseErrorsTest {

    @Test
    void aMalformedValueIsAValidationError() {
        var error = UseCaseErrors.fromException(new InvalidValueException("Invalid email address"));

        assertThat(error).isInstanceOf(ValidationError.class);
        assertThat(error.getMessage()).isEqualTo("Invalid email address");
    }

    @Test
    void aBrokenRuleIsAConflict() {
        var error = UseCaseErrors.fromException(new RuleViolationException("Cannot transition from A to B"));

        assertThat(error).isInstanceOf(ConflictError.class);
        assertThat(error.getMessage()).isEqualTo("Cannot transition from A to B");
    }

    @Test
    void aPlainIllegalArgumentIsUnexpectedAndHidesItsMessage() {
        var error = UseCaseErrors.fromException(new IllegalArgumentException("newStatus must not be null"));

        assertThat(error).isInstanceOf(UnexpectedError.class);
        assertThat(error.getMessage()).isEqualTo(UnexpectedError.PUBLIC_MESSAGE);
    }

    @Test
    void aPlainIllegalStateIsUnexpectedAndHidesItsMessage() {
        var error = UseCaseErrors.fromException(new IllegalStateException("Home team not loaded for match 1"));

        assertThat(error).isInstanceOf(UnexpectedError.class);
        assertThat(error.getMessage()).isEqualTo(UnexpectedError.PUBLIC_MESSAGE);
    }
}
