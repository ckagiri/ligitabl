package com.ligitabl.model.auth;

import static com.ligitabl.model.validator.AssertionUtils.assertArgumentLength;
import static com.ligitabl.model.validator.AssertionUtils.assertArgumentNotEmpty;

public sealed interface Password {

    /**
     * Plaintext password (never stored, only used for validation)
     */
    record Plaintext(String value) implements Password {

        private static final int MIN_LENGTH = 8;
        private static final int MAX_LENGTH = 100;

        public Plaintext {
            assertArgumentLength(
                    value, MIN_LENGTH, MAX_LENGTH, "Password must be at least " + MIN_LENGTH + " characters");
        }

        public static Plaintext create(String value) {
            assertArgumentNotEmpty(value, "Password value cannot be empty");
            assertArgumentLength(value, MAX_LENGTH, "Password must be at most " + MAX_LENGTH + " characters");
            assertArgumentLength(
                    value, MIN_LENGTH, MAX_LENGTH, "Password must be at least " + MIN_LENGTH + " characters");

            return new Plaintext(value);
        }
    }

    /**
     * Hashed password (stored in database)
     */
    record Hashed(String value) implements Password {

        public Hashed {
            assertArgumentNotEmpty(value, "Hashed password cannot be empty");
        }

        /**
         * Create a hashed password (assumes already hashed).
         */
        public static Hashed of(String hashedValue) {
            return new Hashed(hashedValue);
        }
    }
}
