package com.ligitabl.api.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Adapters (rest, web, scheduling, runners) depend on use cases, never the reverse. Use cases reach
 * outbound adapters (the football-data client, Slack alerts) through ports; the files that still
 * call them directly are listed in {@link #OUTBOUND_BASELINE}, which may only shrink.
 */
class UseCaseDependencyRuleTest {

    private static final Path USECASE_SRC = Path.of("src/main/java/com/ligitabl/api/usecase");
    private static final Pattern ADAPTER_REF =
            Pattern.compile("\\bcom\\.ligitabl\\.api\\.(rest|web|scheduling|runners)\\.");
    private static final Pattern OUTBOUND_ADAPTER_REF =
            Pattern.compile("\\bcom\\.ligitabl\\.api\\.(client\\.|notification\\.AdminNotificationService\\b)");

    private static final Set<String> OUTBOUND_BASELINE = Set.of();

    @Test
    void useCasesDoNotDependOnAdapters() throws IOException {
        assertThat(refs(ADAPTER_REF)).isEmpty();
    }

    @Test
    void noNewUseCaseDependsOnOutboundAdapters() throws IOException {
        List<String> violations = refs(OUTBOUND_ADAPTER_REF).stream()
                .filter(ref -> !OUTBOUND_BASELINE.contains(fileOf(ref)))
                .toList();
        assertThat(violations)
                .as("depend on a port instead; see docs/backend-dev.md")
                .isEmpty();
    }

    @Test
    void outboundBaselineHasNoStaleEntries() throws IOException {
        Set<String> stillViolating = refs(OUTBOUND_ADAPTER_REF).stream()
                .map(UseCaseDependencyRuleTest::fileOf)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(OUTBOUND_BASELINE)
                .as("these files no longer reach an outbound adapter; remove them from the baseline")
                .allMatch(stillViolating::contains);
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

    private static String fileOf(String ref) {
        return ref.substring(0, ref.indexOf(": "));
    }
}
