package com.ligitabl.model.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EmailTest {

    @Test
    void shouldCreateValidEmail() {
        Email email = Email.create("test@example.com");
        assertEquals("test@example.com", email.value());
    }

    @Test
    void shouldNormalizeEmailToLowercase() {
        Email email = Email.create("Test@Example.COM");
        assertEquals("test@example.com", email.value());
    }

    @Test
    void shouldTrimWhitespace() {
        Email email = Email.create("  test@example.com  ");
        assertEquals("test@example.com", email.value());
    }

    @Test
    void shouldRejectInvalidEmailFormats() {
        String[] invalidEmails = {
            "invalid-email",
            "missing-at-sign.com",
            "@no-local-part.com",
            "no-domain@",
            "spaces in@email.com",
            "double@@at.com"
        };

        for (String invalidEmail : invalidEmails) {
            assertThrows(IllegalArgumentException.class, () -> Email.create(invalidEmail), "email=" + invalidEmail);
        }
    }

    @Test
    void shouldRejectNullEmail() {
        assertThrows(IllegalArgumentException.class, () -> Email.create(null));
    }

    @Test
    void shouldRejectEmptyEmail() {
        assertThrows(IllegalArgumentException.class, () -> Email.create(""));
    }

    @Test
    void shouldRejectBlankEmail() {
        assertThrows(IllegalArgumentException.class, () -> Email.create("   "));
    }

    @Test
    void shouldBeEqualWhenSameValue() {
        Email email1 = Email.create("test@example.com");
        Email email2 = Email.create("test@example.com");

        assertEquals(email1, email2);
        assertEquals(email1.hashCode(), email2.hashCode());
    }

    @Test
    void toStringShouldReturnValue() {
        Email email = Email.create("test@example.com");
        assertEquals("test@example.com", email.toString());
    }
}
