package com.ligitabl.api.testsupport;

import com.ligitabl.api.LigitablApplication;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * The api module's production classes, imported once per test JVM for the ArchUnit rule tests.
 *
 * <p>Name packages in a rule with {@link #pkg}, never a string literal. It resolves against the
 * root package of {@link LigitablApplication}, so a rename of the root carries over, and it throws
 * when the package is gone, so a stale name fails the rule instead of matching nothing.
 */
public final class ApiClasses {

    public static final String ROOT = LigitablApplication.class.getPackageName();

    public static final JavaClasses MAIN = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    private ApiClasses() {}

    /** {@code name} under the root, with its subpackages, as an ArchUnit package pattern. */
    public static String pkg(String name) {
        String full = ROOT + "." + name;
        if (!MAIN.containPackage(full)) {
            throw new IllegalStateException("no package " + full + "; update the rule");
        }
        return full + "..";
    }
}
