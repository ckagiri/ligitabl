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
 * Every {@code *UseCase} is entered through {@code execute}, overloaded by input type when one use
 * case has more than one trigger. Callers then never have to learn a per-class verb.
 */
class UseCaseEntryPointRuleTest {

    private static final Path USECASE_SRC = Path.of("src/main/java/com/ligitabl/api/usecase");
    private static final Pattern EXECUTE = Pattern.compile("^\\s+public\\s[^=;(]*\\bexecute\\(", Pattern.MULTILINE);

    @Test
    void everyUseCaseHasAnExecuteMethod() throws IOException {
        assertThat(USECASE_SRC).isDirectory();
        List<String> missing;
        try (Stream<Path> files = Files.walk(USECASE_SRC)) {
            missing = files.filter(p -> p.getFileName().toString().endsWith("UseCase.java"))
                    .filter(p -> !EXECUTE.matcher(read(p)).find())
                    .map(p -> USECASE_SRC.relativize(p).toString())
                    .toList();
        }
        assertThat(missing)
                .as("name the entry point execute; see docs/architecture.md")
                .isEmpty();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
