package com.ligitabl.model.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ContestCodeGeneratorTest {

    private static final String ALPHABET = "23456789abcdefghjkmnpqrstuvwxyz";
    private final ContestCodeGenerator generator = new ContestCodeGenerator();

    @Test
    void generatedCodeIsExactlySixCharacters() {
        String code = generator.generate();
        assertThat(code).hasSize(6);
    }

    @Test
    void allCharactersBelongToAlphabet() {
        for (int i = 0; i < 50; i++) {
            String code = generator.generate();
            for (char c : code.toCharArray()) {
                assertThat(ALPHABET).contains(String.valueOf(c));
            }
        }
    }

    @Test
    void alphabetExcludesAmbiguousCharacters() {
        assertThat(ALPHABET).doesNotContain("0", "1", "O", "I", "l", "o", "i");
    }

    @Test
    void generatedCodesAreAlwaysLowercase() {
        for (int i = 0; i < 50; i++) {
            String code = generator.generate();
            assertThat(code).isEqualTo(code.toLowerCase());
        }
    }

    @Test
    void consecutiveCallsProduceDifferentValues() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            codes.add(generator.generate());
        }
        assertThat(codes.size()).isGreaterThan(95);
    }
}
