package com.ligitabl.api.testsupport;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Identifier generators for test fixtures.
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
