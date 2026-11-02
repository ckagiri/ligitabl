package com.ligitabl.api;

import static com.ligitabl.api.testsupport.ApiClasses.MAIN;
import static com.ligitabl.api.testsupport.ApiClasses.pkg;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.ligitabl.model.repo.UserRepo;

/**
 * Outbound adapters translate between the core and the outside world; they don't read or write the
 * domain's repositories. An adapter that does has started making decisions, which belong in a use case.
 */
class OutboundAdapterRuleTest {

    @Test
    void outboundAdaptersDoNotUseRepositories() {
        noClasses()
                .that()
                .resideInAnyPackage(pkg("notification"), pkg("client"))
                .should()
                .dependOnClassesThat()
                .resideInAPackage(UserRepo.class.getPackageName() + "..")
                .because("the logic belongs in a use case; see docs/architecture.md")
                .check(MAIN);
    }
}
