package com.ligitabl.api.auth.crypto;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ligitabl.api.shared.Either;
import com.ligitabl.model.auth.PublicId;
import com.ligitabl.model.domain.service.PublicIdGenerator;

@Component
public class Base56PublicIdGenerator implements PublicIdGenerator {

    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final BigInteger BASE = BigInteger.valueOf(56);

    @Override
    public PublicId generate(UUID uuid) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(uuid.toString().getBytes(StandardCharsets.UTF_8));
            String encoded = encode(hash);

            // Truncate to 10 characters
            String truncated = encoded.substring(0, 10);

            // PublicId.create() validates the format
            return Either.catching(() -> PublicId.create(truncated))
                    .getOrElseThrow(error ->
                            new IllegalStateException("Generated invalid public ID: " + error.getMessage(), error));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    private String encode(byte[] bytes) {
        BigInteger number = new BigInteger(1, bytes);

        if (number.equals(BigInteger.ZERO)) {
            return String.valueOf(ALPHABET.charAt(0));
        }

        StringBuilder result = new StringBuilder();

        while (number.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] divMod = number.divideAndRemainder(BASE);
            number = divMod[0];
            int remainder = divMod[1].intValue();
            result.insert(0, ALPHABET.charAt(remainder));
        }

        return result.toString();
    }
}
