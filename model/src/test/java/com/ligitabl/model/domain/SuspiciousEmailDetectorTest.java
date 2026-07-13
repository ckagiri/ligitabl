package com.ligitabl.model.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SuspiciousEmailDetectorTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "b.ohenus.u.z68@gmail.com",
                "z.abev.o.k.u.s.a.g.u2.0@gmail.com",
                "ka.c.ito.poji.56@gmail.com",
                "ni.ckm.ar.p.r.i.ce.mar.king@gmail.com",
                "j.u.s.ti.n.leg.er.2.36.9@gmail.com"
            })
    void knownSuspiciousEmailsScoreAboveThreshold(String email) {
        var result = SuspiciousEmailDetector.analyze(email);
        assertTrue(
                result.score() >= SuspiciousEmailDetector.SUSPICION_THRESHOLD,
                email + " should score >= 50 but scored " + result.score());
        assertTrue(result.isSuspicious());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "sales@idsmachining.com",
                "john.doe@gmail.com",
                "sarah.oconnor@yahoo.com",
                "michael.j.fox@gmail.com"
            })
    void knownNormalEmailsScoreBelowThreshold(String email) {
        var result = SuspiciousEmailDetector.analyze(email);
        assertTrue(
                result.score() < SuspiciousEmailDetector.SUSPICION_THRESHOLD,
                email + " should score < 50 but scored " + result.score());
        assertFalse(result.isSuspicious());
    }

    @Test
    void middleInitialDotDoesNotFalsePositive() {
        var result = SuspiciousEmailDetector.analyze("michael.j.fox@gmail.com");
        assertEquals(0, result.score());
    }

    @Test
    void trailingDigitClusterFiresOnDeDottedLocalPart() {
        var result = SuspiciousEmailDetector.analyze("j.u.s.ti.n.leg.er.2.36.9@gmail.com");
        assertTrue(result.reasons().contains("Trailing digit cluster"));
    }

    @Test
    void threeSegmentFragmentationIsDeliberatelyNotFlagged() {
        var result = SuspiciousEmailDetector.analyze("a.b.c@gmail.com");
        assertFalse(result.reasons().stream().anyMatch(r -> r.contains("fragmented")));
        assertTrue(result.score() < SuspiciousEmailDetector.SUSPICION_THRESHOLD);
    }

    @ParameterizedTest
    @CsvSource({"user@mailinator.com", "user@sub.mailinator.com", "user@guerrillamail.com"})
    void disposableDomainsAreFlaggedIncludingSubdomains(String email) {
        var result = SuspiciousEmailDetector.analyze(email);
        assertTrue(result.reasons().contains("Disposable/temp-mail domain"));
        assertTrue(result.isSuspicious());
    }

    @Test
    void nullOrMalformedEmailScoresMaximum() {
        assertEquals(100, SuspiciousEmailDetector.analyze(null).score());
        assertEquals(100, SuspiciousEmailDetector.analyze("not-an-email").score());
    }

    @Test
    void reasonsExplainWhyARowWasFlagged() {
        var result = SuspiciousEmailDetector.analyze("b.ohenus.u.z68@gmail.com");
        assertFalse(result.reasons().isEmpty());
    }
}
