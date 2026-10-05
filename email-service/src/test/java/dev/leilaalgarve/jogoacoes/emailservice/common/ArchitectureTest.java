package dev.leilaalgarve.jogoacoes.emailservice.common;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Structural rules for email-service (spec 05-030): the API-KEY library and the {@code X-API-Key}
 * header stay inside the {@code auth} package, the single point that resolves a key into a
 * client (spec 05-025, "Requisitos não-funcionais"). Production code only: test support issues
 * real keys with the library's hasher and repository on purpose.
 */
class ArchitectureTest {

    private static final String BASE_PACKAGE = "dev.leilaalgarve.jogoacoes.emailservice";
    private static final String AUTH_PACKAGE = BASE_PACKAGE + ".auth..";
    private static final String API_KEY_LIBRARY = "dev.leilaalgarve.apikey..";

    private final JavaClasses productionClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);

    @Test
    void onlyTheAuthPackageDependsOnTheApiKeyLibrary() {
        // EmailServiceApplication names the library's packages as strings in its scan
        // annotations -- not a class dependency, so it needs no exception here.
        noClasses().that().resideOutsideOfPackage(AUTH_PACKAGE)
                .should().dependOnClassesThat().resideInAPackage(API_KEY_LIBRARY)
                .check(productionClasses);
    }

    @Test
    void onlyTheAuthPackageReadsRequestHeaders() {
        // X-API-Key is the only header this service reads; the rest of the code gets the client
        // from ClientIdentityResolver, never from the raw header.
        noClasses().that().resideOutsideOfPackage(AUTH_PACKAGE)
                .should().callMethod(HttpServletRequest.class, "getHeader", String.class)
                .orShould().callMethod(HttpServletRequest.class, "getHeaders", String.class)
                .check(productionClasses);
    }
}
