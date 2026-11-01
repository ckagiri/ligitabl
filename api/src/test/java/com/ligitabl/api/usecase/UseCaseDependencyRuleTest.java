package com.ligitabl.api.usecase;

import static com.ligitabl.api.testsupport.ApiClasses.MAIN;
import static com.ligitabl.api.testsupport.ApiClasses.pkg;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

/**
 * Adapters (rest, web, scheduling, runners) depend on use cases, never the reverse. Use cases reach
 * outbound adapters (client, notification, auth) through ports, which live in usecase.
 */
class UseCaseDependencyRuleTest {

    @Test
    void useCasesDoNotDependOnAdapters() {
        noClasses()
                .that()
                .resideInAPackage(pkg("usecase"))
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(pkg("rest"), pkg("web"), pkg("scheduling"), pkg("runners"))
                .check(MAIN);
    }

    @Test
    void useCasesDoNotDependOnOutboundAdapters() {
        noClasses()
                .that()
                .resideInAPackage(pkg("usecase"))
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(pkg("client"), pkg("notification"), pkg("auth"))
                .because("a use case depends on a port instead; see docs/architecture.md")
                .check(MAIN);
    }
}
