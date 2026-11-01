package com.ligitabl.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Outbound adapters translate between the core and the outside world; they don't read or write the
 * domain's repositories. An adapter that does has started making decisions, which belong in a use case.
 */
class OutboundAdapterRuleTest {

    private static final List<Path> OUTBOUND_SRC = List.of(
            Path.of("src/main/java/com/ligitabl/api/notification"), Path.of("src/main/java/com/ligitabl/api/client"));
    private static final Pattern REPO_REF = Pattern.compile("\\bcom\\.ligitabl\\.model\\.repo\\.");

    @Test
    void outboundAdaptersDoNotUseRepositories() throws IOException {
        assertThat(refs())
                .as("move the logic into a use case; see docs/architecture.md")
                .isEmpty();
    }

    private static List<String> refs() throws IOException {
        var found = new java.util.ArrayList<String>();
        for (Path root : OUTBOUND_SRC) {
            assertThat(root).isDirectory();
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file :
                        files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    for (String line : Files.readAllLines(file)) {
                        if (REPO_REF.matcher(line).find()) {
                            found.add(file + ": " + line.strip());
                        }
                    }
                }
            }
        }
        return found;
    }
}
