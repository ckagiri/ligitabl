package com.ligitabl.api.usecase;

import static com.ligitabl.api.testsupport.ApiClasses.MAIN;
import static com.ligitabl.api.testsupport.ApiClasses.pkg;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Every {@code *UseCase} is entered through {@code execute}, overloaded by input type when one use
 * case has more than one trigger. Callers then never have to learn a per-class verb.
 */
class UseCaseEntryPointRuleTest {

    private static final DescribedPredicate<JavaClass> USE_CASES = resideInAPackage(pkg("usecase"))
            .and(simpleNameEndingWith("UseCase"))
            .and(JavaClass.Predicates.TOP_LEVEL_CLASSES)
            .as("use cases");

    private static final ArchCondition<JavaClass> HAVE_EXECUTE = new ArchCondition<>("have a public execute method") {
        @Override
        public void check(JavaClass useCase, ConditionEvents events) {
            boolean found = useCase.getMethods().stream()
                    .anyMatch(m ->
                            m.getName().equals("execute") && m.getModifiers().contains(JavaModifier.PUBLIC));
            if (!found) {
                events.add(SimpleConditionEvent.violated(useCase, useCase.getName() + " has no public execute method"));
            }
        }
    };

    @Test
    void everyUseCaseHasAnExecuteMethod() {
        classes()
                .that(USE_CASES)
                .should(HAVE_EXECUTE)
                .because("the entry point is named execute; see docs/architecture.md")
                .check(MAIN);
    }

    @Test
    void useCasesExposeNothingButExecute() {
        methods()
                .that()
                .areDeclaredInClassesThat(USE_CASES)
                .and()
                .arePublic()
                .should()
                .haveName("execute")
                .because("a second trigger is another execute overload; see docs/architecture.md")
                .check(MAIN);
    }

    @Test
    void useCasesHaveNoPublicNestedTypes() {
        noClasses()
                .that()
                .areMemberClasses()
                .and(DescribedPredicate.describe(
                        "are declared in a use case",
                        c -> c.getEnclosingClass().map(USE_CASES::test).orElse(false)))
                .should()
                .bePublic()
                .because("contract types are top-level files; see docs/architecture.md")
                .allowEmptyShould(true)
                .check(MAIN);
    }
}
