package com.ligitabl.api.auth.crypto;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.ligitabl.model.auth.Password;
import com.ligitabl.model.domain.service.PasswordHasher;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BCryptPasswordHasher implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    @Override
    public Password.Hashed hash(Password.Plaintext plaintext) {
        String hashed = passwordEncoder.encode(plaintext.value());
        return Password.Hashed.of(hashed);
    }

    @Override
    public boolean verify(Password.Plaintext plaintext, Password.Hashed hashed) {
        return passwordEncoder.matches(plaintext.value(), hashed.value());
    }
}
