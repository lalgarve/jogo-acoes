package dev.leilaalgarve.jogoacoes.common;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAccess;
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
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Structural rules for module boundaries (spec 05-006). More rules can join this class as the
 * project's modularization grows -- one architecture test, not one class per rule.
 */
class ArchitectureTest {

    private static final String BASE_PACKAGE = "dev.leilaalgarve.jogoacoes";
    private static final String EMAIL_SERVICE_PACKAGE = BASE_PACKAGE + ".emailservice..";
    private static final String EMAIL_CLIENT_API_PACKAGE = BASE_PACKAGE + ".email.client.api..";

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

    /**
     * Spec 05-029: modules talk to each other only through services -- a repository is an
     * internal detail of the module that owns it. Production code only: test fixtures/steps
     * deliberately use repositories from any module to arrange and assert real database state.
     */
    @Test
    void repositoriesAreOnlyAccessedFromTheirOwnModule() {
        JavaClasses importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);

        ArchRule rule = classes()
                .that().areInterfaces()
                .and().areAssignableTo(Repository.class)
                .should(onlyBeAccessedFromTheirOwnModule());

        rule.check(importedClasses);
    }

    /**
     * Spec 05-034: app and email-service are separately deployable services and share no Java
     * code. They even share the root package (email-service lives under
     * {@code dev.leilaalgarve.jogoacoes.emailservice}), so a class from email-service on app's
     * classpath would silently join this analysis -- this rule makes that visible.
     */
    @Test
    void appDoesNotContainOrDependOnEmailServiceClasses() {
        JavaClasses importedClasses = productionClasses();

        noClasses().should().resideInAPackage(EMAIL_SERVICE_PACKAGE)
                .orShould().dependOnClassesThat().resideInAPackage(EMAIL_SERVICE_PACKAGE)
                .check(importedClasses);
    }

    /**
     * Spec 05-034: e-mail goes through email-service over HTTP. The queue belongs to
     * email-service and email-lambda now, so nothing in app talks to SQS.
     */
    @Test
    void appDoesNotTalkToTheEmailQueue() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage(
                        "io.awspring.cloud.sqs..", "software.amazon.awssdk.services.sqs..")
                .check(productionClasses());
    }

    /** Spec 05-034: e-mail content is rendered by SES from email-service templates, not by app. */
    @Test
    void appDoesNotRenderEmailsWithThymeleaf() {
        noClasses().should().dependOnClassesThat().resideInAPackage("org.thymeleaf..")
                .check(productionClasses());
    }

    /**
     * Spec 05-034: the Feign interface generated from docs/openapi-email-service.yaml is only
     * called by EmailServiceGateway, which translates remote failures into app exceptions.
     */
    @Test
    void generatedEmailServiceApiIsOnlyUsedByTheGateway() {
        classes().that().resideInAPackage(EMAIL_CLIENT_API_PACKAGE)
                .should().onlyHaveDependentClassesThat(
                        resideInAPackage(EMAIL_CLIENT_API_PACKAGE).or(simpleName("EmailServiceGateway")))
                .check(productionClasses());
    }

    /** Spec 05-034: other modules send e-mail through EmailSender, never through the HTTP client. */
    @Test
    void onlyTheEmailModuleUsesTheEmailServiceClient() {
        noClasses().that().resideOutsideOfPackage(BASE_PACKAGE + ".email..")
                .should().dependOnClassesThat().resideInAPackage(BASE_PACKAGE + ".email.client..")
                .check(productionClasses());
    }

    private static JavaClasses productionClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);
    }

    private static ArchCondition<JavaClass> onlyBeAccessedFromTheirOwnModule() {
        return new ArchCondition<>("only be accessed from their own module") {
            @Override
            public void check(JavaClass repository, ConditionEvents events) {
                String owner = moduleOf(repository);
                Set<JavaClass> origins = repository.getDirectDependenciesToSelf().stream()
                        .map(Dependency::getOriginClass)
                        .filter(origin -> !moduleOf(origin).equals(owner))
                        .collect(Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(JavaClass::getName))));
                for (JavaClass origin : origins) {
                    // One violation per call site -- that's what has to change. Filtering the
                    // origin's own accesses by target owner (instead of repository
                    // .getAccessesToSelf()) also catches inherited methods like save/findById,
                    // whose declaring class is Spring Data's, not this repository.
                    List<JavaAccess<?>> calls = origin.getAccessesFromSelf().stream()
                            .filter(access -> access.getTargetOwner().equals(repository))
                            .sorted(Comparator.comparing(JavaAccess::getLineNumber))
                            .toList();
                    calls.forEach(call -> events.add(SimpleConditionEvent.violated(call, call.getDescription())));
                    if (calls.isEmpty()) {
                        events.add(SimpleConditionEvent.violated(origin, origin.getFullName() + " depends on "
                                + repository.getFullName() + ", which belongs to module " + owner));
                    }
                }
            }
        };
    }

    /** First package segment below the base package: {@code competition.exception} is {@code competition}. */
    private static String moduleOf(JavaClass javaClass) {
        String relative = javaClass.getPackageName().substring(BASE_PACKAGE.length());
        if (relative.startsWith(".")) {
            relative = relative.substring(1);
        }
        int dot = relative.indexOf('.');
        return dot < 0 ? relative : relative.substring(0, dot);
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
