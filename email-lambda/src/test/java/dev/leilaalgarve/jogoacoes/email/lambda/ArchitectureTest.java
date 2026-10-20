package dev.leilaalgarve.jogoacoes.email.lambda;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

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
    /**
     * Spec 05-053: no test starts a container -- everything a test uses is brought up before,
     * outside Maven (constitution, "Testes exigem a infraestrutura de pé").
     */
    @Test
    void noTestStartsContainersWithTestcontainers() {
        JavaClasses testClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);

        noClasses().should().dependOnClassesThat().resideInAPackage("org.testcontainers..")
                .check(testClasses);
    }

    /**
     * Spec 05-053: Quarkus Dev Services is configuration, not a class dependency, so the rule above
     * does not see it -- left on, it starts a LocalStack container with Testcontainers whenever no
     * endpoint is configured. Read from the file, not from a {@code @QuarkusTest}: by the time one
     * runs, Dev Services would already have started the container.
     */
    @Test
    void devServicesIsSwitchedOffInTests() throws IOException {
        Properties testProperties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/test/resources/application.properties"))) {
            testProperties.load(reader);
        }

        assertThat(testProperties.getProperty("quarkus.devservices.enabled")).isEqualTo("false");
    }
}
