package com.ligitabl.model;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaPackage;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * The domain is split into contexts that depend on each other in one direction only, and it knows
 * nothing of persistence: repos, read models and the outbox sit outside it. See docs/architecture.md.
 */
class ModelDependencyRuleTest {

    private static final String ROOT = "com.ligitabl.model";

    private static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    /** Each context under domain, and the other contexts it may depend on. */
    private static final Map<String, Set<String>> CONTEXTS = Map.of(
            "identity", Set.of(),
            "league", Set.of(),
            "prediction", Set.of("league"),
            "contest", Set.of("league"));

    @Test
    void everyContextIsListed() {
        Set<String> actual = MAIN.getPackage(ROOT + ".domain").getSubpackages().stream()
                .map(JavaPackage::getRelativeName)
                .collect(toSet());
        assertThat(actual).as("add the new context to CONTEXTS").isEqualTo(CONTEXTS.keySet());
    }

    @Test
    void contextsDependOnlyOnTheContextsListed() {
        CONTEXTS.forEach((context, allowed) -> noClasses()
                .that()
                .resideInAPackage(pkg("domain." + context))
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(CONTEXTS.keySet().stream()
                        .filter(other -> !other.equals(context) && !allowed.contains(other))
                        .map(other -> pkg("domain." + other))
                        .toArray(String[]::new))
                .check(MAIN));
    }

    @Test
    void domainDoesNotDependOnPersistenceOrReadModels() {
        noClasses()
                .that()
                .resideInAPackage(pkg("domain"))
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(pkg("repo"), pkg("infra"), pkg("query"), pkg("outbox"))
                .check(MAIN);
    }

    // Jackson is allowed: Lombok's @Jacksonized puts its annotations on value objects read from JSON.
    @Test
    void domainAndRepoAreFreeOfSpringAndJooq() {
        noClasses()
                .that()
                .resideInAnyPackage(pkg("domain"), pkg("repo"))
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "org.jooq..", pkg("infra"))
                .check(MAIN);
    }

    /** Throws when the package is gone, so a stale name fails the rule instead of matching nothing. */
    private static String pkg(String name) {
        String full = ROOT + "." + name;
        if (!MAIN.containPackage(full)) {
            throw new IllegalStateException("no package " + full + "; update the rule");
        }
        return full + "..";
    }
}
