package dev.leilaalgarve.jogoacoes.common;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import dev.leilaalgarve.jogoacoes.login.SecurityConfigContributor;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

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
