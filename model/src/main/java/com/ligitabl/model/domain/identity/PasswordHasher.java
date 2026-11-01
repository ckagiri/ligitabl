package com.ligitabl.model.domain.identity;

public interface PasswordHasher {
    Password.Hashed hash(Password.Plaintext plaintext);

    /** Always false for a null hash, which is an account with no password. */
    boolean verify(Password.Plaintext plaintext, Password.Hashed hashed);
}
