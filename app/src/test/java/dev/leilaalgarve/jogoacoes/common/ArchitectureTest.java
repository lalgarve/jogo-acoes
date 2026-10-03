package dev.leilaalgarve.jogoacoes.common;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import dev.leilaalgarve.jogoacoes.loginsecurity.SecurityConfigContributor;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Structural rules for module boundaries (spec 05-006). More rules can join this class as the
 * project's modularization grows -- one architecture test, not one class per rule.
 */
class ArchitectureTest {

    private static final String BASE_PACKAGE = "dev.leilaalgarve.jogoacoes";

    @Test
    void everyModuleWithARestControllerHasASecurityConfigContributor() {
        JavaClasses importedClasses = new ClassFileImporter().importPackages(BASE_PACKAGE);

        ArchRule rule = classes()
                .that().areAnnotatedWith(RestController.class)
                .should(haveASecurityConfigContributorInTheSamePackage(importedClasses));

        rule.check(importedClasses);
    }

    /**
     * Spec 05-027: `user` and `loginsecurity` are base modules and `link` is generic -- none of
     * them may depend on `loginsession` (or on each other); only `loginsession` depends on all
     * three, never the other way around. Production code only: tests like
     * LinkRouterKeyUniquenessTest deliberately wire the real handlers from other modules.
     */
    @Test
    void userLoginsecurityAndLinkDoNotDependOnEachOtherOrOnLoginsession() {
        JavaClasses importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);

        noClasses().that().resideInAPackage(BASE_PACKAGE + ".user..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE_PACKAGE + ".loginsession..", BASE_PACKAGE + ".loginsecurity..", BASE_PACKAGE + ".link..")
                .check(importedClasses);

        noClasses().that().resideInAPackage(BASE_PACKAGE + ".loginsecurity..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE_PACKAGE + ".loginsession..", BASE_PACKAGE + ".user..", BASE_PACKAGE + ".link..")
                .check(importedClasses);

        noClasses().that().resideInAPackage(BASE_PACKAGE + ".link..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        BASE_PACKAGE + ".loginsession..", BASE_PACKAGE + ".user..", BASE_PACKAGE + ".loginsecurity..")
                .check(importedClasses);
    }

    private static ArchCondition<JavaClass> haveASecurityConfigContributorInTheSamePackage(JavaClasses allClasses) {
        return new ArchCondition<>("have a SecurityConfigContributor in the same package") {
            @Override
            public void check(JavaClass restController, ConditionEvents events) {
                String modulePackage = restController.getPackageName();
                boolean hasContributor = allClasses.stream()
                        .filter(candidate -> !candidate.isInterface())
                        .filter(candidate -> candidate.getPackageName().equals(modulePackage))
                        .anyMatch(candidate -> candidate.isAssignableTo(SecurityConfigContributor.class));

                if (!hasContributor) {
                    String message = restController.getFullName()
                            + " is a @RestController with no SecurityConfigContributor in package " + modulePackage;
                    events.add(SimpleConditionEvent.violated(restController, message));
                }
            }
        };
    }
}
