package com.ligitabl.api.testsupport;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Identifier generators for test fixtures.
 *
 * <p>Exists because the obvious shortcut is wrong in a way that hides: a hex substring of a UUID
 * looks like a valid public id, but {@code PublicId} uses an ambiguity-free alphabet with no
 * {@code 0}, {@code 1}, {@code l} or {@code o}. A fixture built that way passes until a query
 * happens to map the row into a {@code User}, and then fails only on the runs where the random
 * hex contained a forbidden character.
 */
public final class TestIds {

    private static final String PUBLIC_ID_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";

    private TestIds() {}

    /** A public id that satisfies {@code PublicId.create} on every run, not most of them. */
    public static String randomPublicId() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(PUBLIC_ID_ALPHABET.charAt(ThreadLocalRandom.current().nextInt(PUBLIC_ID_ALPHABET.length())));
        }
        return sb.toString();
    }
}
