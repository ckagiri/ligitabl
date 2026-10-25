package com.ligitabl.api.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** Adapters (rest, web, scheduling, runners) depend on use cases, never the reverse. */
class UseCaseDependencyRuleTest {

    private static final Path USECASE_SRC = Path.of("src/main/java/com/ligitabl/api/usecase");
    private static final Pattern ADAPTER_REF =
            Pattern.compile("\\bcom\\.ligitabl\\.api\\.(rest|web|scheduling|runners)\\.");

    @Test
    void useCasesDoNotDependOnAdapters() throws IOException {
        assertThat(USECASE_SRC).isDirectory();
        List<String> violations;
        try (Stream<Path> files = Files.walk(USECASE_SRC)) {
            violations = files.filter(p -> p.toString().endsWith(".java"))
                    .flatMap(UseCaseDependencyRuleTest::adapterRefs)
                    .toList();
        }
        assertThat(violations).isEmpty();
    }

    private static Stream<String> adapterRefs(Path file) {
        try {
            return Files.readAllLines(file).stream()
                    .filter(line -> ADAPTER_REF.matcher(line).find())
                    .map(line -> USECASE_SRC.relativize(file) + ": " + line.strip());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
