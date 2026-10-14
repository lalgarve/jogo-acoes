package dev.leilaalgarve.jogoacoes.configserver;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Spec 05-042: config-server is a separately deployable service and shares no Java code with
 * app or email-service (constitution, "Fronteira entre módulos e serviços"). They all share the
 * root package, so a class from either service on this classpath would land right next to
 * config-server's own -- importing the shared root makes that visible.
 */
class ArchitectureTest {

    private static final String BASE_PACKAGE = "dev.leilaalgarve.jogoacoes.configserver";
    private static final String SHARED_ROOT_PACKAGE = "dev.leilaalgarve.jogoacoes";

    @Test
    void configServerDoesNotContainOrDependOnOtherServicesClasses() {
        JavaClasses sharedRootClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(SHARED_ROOT_PACKAGE);

        noClasses().should().resideOutsideOfPackage(BASE_PACKAGE + "..")
                .orShould().dependOnClassesThat(resideInAPackage(SHARED_ROOT_PACKAGE + "..")
                        .and(resideOutsideOfPackage(BASE_PACKAGE + "..")))
                .check(sharedRootClasses);
    }

    /** Same rule as app's and email-service's (spec 05-035): no test skips itself. */
    @Test
    void noTestSkipsItselfWithAssumptions() {
        JavaClasses testClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);

        noClasses().should().dependOnClassesThat().haveFullyQualifiedName("org.junit.jupiter.api.Assumptions")
                .check(testClasses);
    }
}
