package com.genisource.erp.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Guards the Open Core dependency direction described in ADR-0004.
 *
 * <p>The rule is one-way: a domain module may depend on the platform, never the other way
 * round. Without this test the boundary erodes the first time someone needs "just one
 * entity" from a domain module inside the platform, and splitting the community edition
 * from the paid one quietly stops being possible.
 */
class ModuleBoundaryTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.genisource.erp");
    }

    @Test
    @DisplayName("the platform never depends on a domain module")
    void platformIsIndependent() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.genisource.erp.platform..")
                .should().dependOnClassesThat()
                .resideInAPackage("com.genisource.erp.masterdata..")
                .because("the platform is the shared kernel every other module builds on")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    @DisplayName("the platform never depends on the application entry point")
    void platformDoesNotDependOnBootstrap() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.genisource.erp.platform..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("com.genisource.erp.ErpApplication")
                .because("only bootstrap wires modules together")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    @DisplayName("master data does not depend on the bootstrap")
    void masterDataDoesNotDependOnBootstrap() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.genisource.erp.masterdata..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("com.genisource.erp.ErpApplication")
                .because("a module must be usable without the application that assembles it")
                .allowEmptyShould(true);
        rule.check(classes);
    }

    @Test
    @DisplayName("master data does not reach into a downstream domain module")
    void masterDataStaysInsideItsBoundary() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.genisource.erp.masterdata..")
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.genisource.erp.inventory..",
                        "com.genisource.erp.procurement..",
                        "com.genisource.erp.sales..",
                        "com.genisource.erp.mrp..",
                        "com.genisource.erp.production..",
                        "com.genisource.erp.finance..")
                .because("master data is the base of the dependency graph, not a consumer of it")
                .allowEmptyShould(true);
        rule.check(classes);
    }
}
