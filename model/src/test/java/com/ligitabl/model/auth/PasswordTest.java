package com.ligitabl.model.auth;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PasswordTest {

    @Test
    void shouldCreateValidPlaintextPassword() {
        Password.Plaintext password = Password.Plaintext.create("password123");
        assertEquals("password123", password.value());
    }

    @Test
    void shouldRejectPasswordsShorterThan8Characters() {
        assertThrows(IllegalArgumentException.class, () -> Password.Plaintext.create("short"));
        assertThrows(IllegalArgumentException.class, () -> Password.Plaintext.create("1234567"));
    }

    @Test
    void shouldRejectNullPassword() {
        assertThrows(IllegalArgumentException.class, () -> Password.Plaintext.create(null));
    }

    @Test
    void shouldRejectEmptyPassword() {
        assertThrows(IllegalArgumentException.class, () -> Password.Plaintext.create(""));
    }

    @Test
    void shouldAcceptMinimumLengthPassword() {
        assertDoesNotThrow(() -> Password.Plaintext.create("12345678"));
    }

    @Test
    void shouldAcceptLongPassword() {
        assertDoesNotThrow(() -> Password.Plaintext.create("a".repeat(50)));
    }

    @Test
    void shouldCreateHashedPasswordFromString() {
        String hash = "$2a$10$abcdefghijklmnopqrstuvwxyz";
        Password.Hashed hashed = Password.Hashed.of(hash);
        assertEquals(hash, hashed.value());
    }

    @Test
    void shouldThrowForNullHash() {
        assertThrows(IllegalArgumentException.class, () -> Password.Hashed.of(null));
    }

    @Test
    void shouldThrowForEmptyHash() {
        assertThrows(IllegalArgumentException.class, () -> Password.Hashed.of(""));
    }

    @Test
    void plaintextAndHashedShouldBeDifferentTypes() {
        Password.Plaintext plaintext = Password.Plaintext.create("password123");
        Password.Hashed hashed = Password.Hashed.of("$2a$10$hash");

        assertInstanceOf(Password.class, plaintext);
        assertInstanceOf(Password.class, hashed);
        assertNotEquals(plaintext.getClass(), hashed.getClass());
    }

    @Test
    void shouldHandleAllPasswordTypesWithPatternMatching() {
        Password plaintext = Password.Plaintext.create("password123");
        Password hashed = Password.Hashed.of("$2a$10$hash");

        String plaintextResult = switch (plaintext) {
            case Password.Plaintext p -> "plaintext: " + p.value().length() + " chars";
            case Password.Hashed h -> "hashed";
        };

        String hashedResult = switch (hashed) {
            case Password.Plaintext p -> "plaintext";
            case Password.Hashed h -> "hashed: " + h.value();
        };

        assertTrue(plaintextResult.startsWith("plaintext:"));
        assertTrue(hashedResult.startsWith("hashed:"));
    }
}
