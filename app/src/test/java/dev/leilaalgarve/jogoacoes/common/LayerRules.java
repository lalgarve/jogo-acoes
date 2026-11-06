package dev.leilaalgarve.jogoacoes.common;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.ANNOTATIONS;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.metaAnnotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Spec 05-063: controller -> service -> repository inside every module, with no shortcut and
 * nothing pointing back up. Modules have no subpackage per layer, so a class's layer comes from
 * its role: controllers by annotation, repositories by Spring Data type or annotation, everything
 * else is the service layer. Classes generated from the OpenAPI contract ({@code api}) are left
 * out: controllers implement them, and the code isn't ours.
 *
 * <p>Duplicated in email-service on purpose: the two services share no Java code, not even in
 * tests (spec 05-034).
 */
final class LayerRules {

    static final DescribedPredicate<JavaClass> CONTROLLERS =
            annotatedWith(RestController.class).or(annotatedWith(Controller.class)).<JavaClass>forSubtype()
                    .as("controllers");

    static final DescribedPredicate<JavaClass> REPOSITORIES =
            assignableTo(org.springframework.data.repository.Repository.class)
                    .or(annotatedWith(org.springframework.stereotype.Repository.class).<JavaClass>forSubtype())
                    .as("repositories");

    /**
     * Spring beans that aren't repositories: services, controllers, configuration. Not entities,
     * and not the stereotype annotations themselves ({@code @Repository} is meta-annotated with
     * {@code @Component}).
     */
    static final DescribedPredicate<JavaClass> SERVICES_AND_CONTROLLERS =
            metaAnnotatedWith(Component.class).<JavaClass>forSubtype().and(not(REPOSITORIES)).and(not(ANNOTATIONS)).as("services or controllers");

    private static final DescribedPredicate<JavaClass> GENERATED = resideInAPackage("..api..");

    private static final String[] DATABASE_APIS = {
            "org.springframework.jdbc.core.JdbcTemplate",
            "org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate",
            "org.springframework.jdbc.core.simple.JdbcClient",
            "jakarta.persistence.EntityManager",
            "javax.sql.DataSource"};

    private LayerRules() {
    }

    static ArchRule onlyRepositoriesUseDatabaseApis() {
        return noClasses().that(not(REPOSITORIES)).and(not(GENERATED))
                .should().dependOnClassesThat().haveFullyQualifiedName(DATABASE_APIS[0])
                .orShould().dependOnClassesThat().haveFullyQualifiedName(DATABASE_APIS[1])
                .orShould().dependOnClassesThat().haveFullyQualifiedName(DATABASE_APIS[2])
                .orShould().dependOnClassesThat().haveFullyQualifiedName(DATABASE_APIS[3])
                .orShould().dependOnClassesThat().haveFullyQualifiedName(DATABASE_APIS[4])
                .because("only repositories access the database (spec 05-063)");
    }

    static ArchRule controllersDoNotUseRepositories() {
        return noClasses().that(CONTROLLERS)
                .should().dependOnClassesThat(REPOSITORIES)
                .because("a controller calls services, which call repositories (spec 05-063)");
    }

    static ArchRule nothingDependsOnControllers() {
        return noClasses().that(not(CONTROLLERS)).and(not(GENERATED))
                .should().dependOnClassesThat(CONTROLLERS)
                .because("only Spring calls controllers (spec 05-063)");
    }

    static ArchRule repositoriesDoNotDependOnServicesOrControllers() {
        return noClasses().that(REPOSITORIES)
                .should().dependOnClassesThat(SERVICES_AND_CONTROLLERS)
                .because("a repository only reaches the database, entities and projections (spec 05-063)");
    }
}
