package com.ligitabl.api.shared.errors;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ValidationErrorTest {

    @Test
    void aMessageWithNoFieldHasNoPrefix() {
        assertThat(new ValidationError("Round position must be at least 1").getMessage())
                .isEqualTo("Round position must be at least 1");
    }

    @Test
    void aFieldMessageNamesItsField() {
        var error = new ValidationError(
                List.of(new ValidationMessage("email", "is required"), ValidationMessage.of("Check the form")));

        assertThat(error.getMessage()).isEqualTo("email: is required, Check the form");
    }
}
