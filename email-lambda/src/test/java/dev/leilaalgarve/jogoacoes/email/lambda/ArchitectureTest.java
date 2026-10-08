package dev.leilaalgarve.jogoacoes.email.lambda;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Structural rules for email-lambda, same rule as app's and email-service's ArchitectureTest
 * (spec 05-035): missing infrastructure is an error, never a skip (constitution, "Testes exigem a
 * infraestrutura de pé"), so no test decides at runtime whether to run.
 */
class ArchitectureTest {

    private static final String BASE_PACKAGE = "dev.leilaalgarve.jogoacoes.email.lambda";

    /** Named by string, not by class literal: a class literal would make this test depend on Assumptions itself. */
    @Test
    void noTestSkipsItselfWithAssumptions() {
        JavaClasses testClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);

        noClasses().should().dependOnClassesThat().haveFullyQualifiedName("org.junit.jupiter.api.Assumptions")
                .check(testClasses);
    }
}
