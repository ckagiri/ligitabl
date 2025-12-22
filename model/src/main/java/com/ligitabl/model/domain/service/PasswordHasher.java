package com.ligitabl.model.domain.service;

import com.ligitabl.model.auth.Password;

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
     * @param hashed the hashed password to verify against
     * @return true if the password matches, false otherwise
     */
    boolean verify(Password.Plaintext plaintext, Password.Hashed hashed);
}
