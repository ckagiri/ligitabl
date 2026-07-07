package com.ligitabl.model.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SeasonSlugTest {

    @Test
    void ofAcceptsValidFormat() {
        assertEquals("2024-25", SeasonSlug.of("2024-25").value());
    }

    @Test
    void ofRejectsInvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> SeasonSlug.of("2024-2025"));
    }

    @Test
    void fromShorthandParsesFourDigits() {
        assertEquals(SeasonSlug.of("2025-26"), SeasonSlug.fromShorthand("2526"));
    }

    @Test
    void fromShorthandRejectsWrongLength() {
        assertThrows(IllegalArgumentException.class, () -> SeasonSlug.fromShorthand("252"));
        assertThrows(IllegalArgumentException.class, () -> SeasonSlug.fromShorthand("25260"));
    }

    @Test
    void fromShorthandRejectsNonDigits() {
        assertThrows(IllegalArgumentException.class, () -> SeasonSlug.fromShorthand("25ab"));
    }

    @Test
    void toShorthandIsInverseOfFromShorthand() {
        assertEquals("2526", SeasonSlug.of("2025-26").toShorthand());
        assertEquals("9900", SeasonSlug.of("2099-00").toShorthand());
    }

    @Test
    void shorthandRoundTrip() {
        SeasonSlug original = SeasonSlug.of("2024-25");
        assertEquals(original, SeasonSlug.fromShorthand(original.toShorthand()));
    }
}
