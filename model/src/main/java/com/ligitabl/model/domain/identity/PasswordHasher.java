package com.ligitabl.model.domain.identity;

public interface PasswordHasher {
    /**
     * Hash a plaintext password.
     *
     * @param plaintext the plaintext password
     * @return the hashed password
     */
    Password.Hashed hash(Password.Plaintext plaintext);

    /**
     * Verify a plaintext password against a hash.
     *
     * @param plaintext the plaintext password to verify
     * @param hashed the hashed password to verify against; null for an account with no password
     * @return true if the password matches, false otherwise (always false for a null hash)
     */
    boolean verify(Password.Plaintext plaintext, Password.Hashed hashed);
}
