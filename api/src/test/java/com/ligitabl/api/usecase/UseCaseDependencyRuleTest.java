package com.ligitabl.api.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Adapters (rest, web, scheduling, runners) depend on use cases, never the reverse. Use cases reach
 * outbound adapters (client, notification, auth) through ports, which live in usecase.
 */
class UseCaseDependencyRuleTest {

    private static final Path USECASE_SRC = Path.of("src/main/java/com/ligitabl/api/usecase");
    private static final Pattern ADAPTER_REF =
            Pattern.compile("\\bcom\\.ligitabl\\.api\\.(rest|web|scheduling|runners)\\.");
    private static final Pattern OUTBOUND_ADAPTER_REF =
            Pattern.compile("\\bcom\\.ligitabl\\.api\\.(client|notification|auth)\\.");

    @Test
    void useCasesDoNotDependOnAdapters() throws IOException {
        assertThat(refs(ADAPTER_REF)).isEmpty();
    }

    @Test
    void useCasesDoNotDependOnOutboundAdapters() throws IOException {
        assertThat(refs(OUTBOUND_ADAPTER_REF))
                .as("depend on a port instead; see docs/architecture.md")
                .isEmpty();
    }

    private static List<String> refs(Pattern pattern) throws IOException {
        assertThat(USECASE_SRC).isDirectory();
        try (Stream<Path> files = Files.walk(USECASE_SRC)) {
            return files.filter(p -> p.toString().endsWith(".java"))
                    .flatMap(file -> refs(file, pattern))
                    .toList();
        }
    }

    private static Stream<String> refs(Path file, Pattern pattern) {
        try {
            return Files.readAllLines(file).stream()
                    .filter(line -> pattern.matcher(line).find())
                    .map(line -> USECASE_SRC.relativize(file) + ": " + line.strip());
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
