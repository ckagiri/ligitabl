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

    private static final String MODIFIERS = "^    public\\s+(?:static\\s+)?(?:final\\s+)?(?:sealed\\s+)?";
    private static final String TYPE_KEYWORD = "(?:record|class|interface|enum)\\b";

    /** A public member method of the use case class: one return type, then the name. */
    private static final Pattern PUBLIC_METHOD = Pattern.compile(
            MODIFIERS + "(?!" + TYPE_KEYWORD + ")(?:<[^>]+>\\s+)?[\\w<>\\[\\], ?.]+\\s+(\\w+)\\s*\\(",
            Pattern.MULTILINE);

    private static final Pattern PUBLIC_NESTED_TYPE =
            Pattern.compile(MODIFIERS + TYPE_KEYWORD + "\\s+(\\w+)", Pattern.MULTILINE);

    @Test
    void everyUseCaseHasAnExecuteMethod() throws IOException {
        List<String> missing = useCaseFiles()
                .filter(p -> !EXECUTE.matcher(read(p)).find())
                .map(p -> USECASE_SRC.relativize(p).toString())
                .toList();
        assertThat(missing)
                .as("name the entry point execute; see docs/architecture.md")
                .isEmpty();
    }

    @Test
    void useCasesExposeNothingButExecute() throws IOException {
        List<String> extra = useCaseFiles()
                .flatMap(p -> {
                    String src = read(p);
                    String file = USECASE_SRC.relativize(p).toString();
                    Stream<String> methods = PUBLIC_METHOD
                            .matcher(src)
                            .results()
                            .map(m -> m.group(1))
                            .filter(name -> !name.equals("execute"))
                            .map(name -> file + ": method " + name);
                    Stream<String> types =
                            PUBLIC_NESTED_TYPE.matcher(src).results().map(m -> file + ": nested type " + m.group(1));
                    return Stream.concat(methods, types);
                })
                .toList();
        assertThat(extra)
                .as("a second trigger is another execute overload; contract types are top-level files."
                        + " See docs/architecture.md")
                .isEmpty();
    }

    private static Stream<Path> useCaseFiles() throws IOException {
        assertThat(USECASE_SRC).isDirectory();
        try (Stream<Path> files = Files.walk(USECASE_SRC)) {
            return files.filter(p -> p.getFileName().toString().endsWith("UseCase.java")).toList().stream();
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
