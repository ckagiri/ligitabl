package com.ligitabl.api.auth.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.ligitabl.model.domain.identity.Password;

class BCryptPasswordHasherTest {

    private final BCryptPasswordHasher hasher = new BCryptPasswordHasher(new BCryptPasswordEncoder(4));
    private final Password.Plaintext plaintext = Password.Plaintext.create("password123");

    @Test
    void verifiesItsOwnHash() {
        Password.Hashed hashed = hasher.hash(plaintext);

        assertThat(hasher.verify(plaintext, hashed)).isTrue();
        assertThat(hasher.verify(Password.Plaintext.create("wrong-password"), hashed))
                .isFalse();
    }

    /** A Google-only account has no hash; this used to NPE and surface as a 500 on login. */
    @Test
    void nullHash_neverMatches() {
        assertThat(hasher.verify(plaintext, null)).isFalse();
    }
}
