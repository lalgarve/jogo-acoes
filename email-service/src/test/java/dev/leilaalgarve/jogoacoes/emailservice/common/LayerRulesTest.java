package dev.leilaalgarve.jogoacoes.emailservice.common;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-063: each layer rule fails on the examples that break it, and only on those -- a rule
 * that never fails would pass unnoticed. The examples live outside the project's packages.
 */
class LayerRulesTest {

    private static final JavaClasses EXAMPLES = new ClassFileImporter().importPackages("dev.leilaalgarve.layerexamples");

    @Test
    void controllerOrServiceUsingADatabaseApiFails() {
        assertThat(violators(LayerRules.onlyRepositoriesUseDatabaseApis()))
                .contains("ControllerUsingJdbc", "ServiceUsingJdbc")
                .doesNotContain("JdbcExampleRepository", "ExampleService");
    }

    @Test
    void controllerUsingARepositoryFails() {
        assertThat(violators(LayerRules.controllersDoNotUseRepositories()))
                .contains("ControllerUsingRepository")
                .doesNotContain("ExampleController");
    }

    @Test
    void serviceUsingAControllerFails() {
        assertThat(violators(LayerRules.nothingDependsOnControllers()))
                .contains("ServiceUsingController")
                .doesNotContain("ExampleService", "ExampleController");
    }

    @Test
    void repositoryUsingAServiceFails() {
        assertThat(violators(LayerRules.repositoriesDoNotDependOnServicesOrControllers()))
                .contains("RepositoryUsingService")
                .doesNotContain("JdbcExampleRepository", "ExampleRepository");
    }

    /**
     * The simple names of the classes the rule reports. Each detail line starts with the origin
     * of the dependency in angle brackets ({@code Field <Origin.field> has type ...}), so the
     * origin class is the example whose full name opens that first bracket.
     */
    private static List<String> violators(ArchRule rule) {
        List<String> details = rule.evaluate(EXAMPLES).getFailureReport().getDetails();
        return EXAMPLES.stream()
                .filter(example -> details.stream().anyMatch(detail -> originOf(detail).equals(example.getName())
                        || originOf(detail).startsWith(example.getName() + ".")))
                .map(example -> example.getSimpleName())
                .toList();
    }

    private static String originOf(String detail) {
        int start = detail.indexOf('<') + 1;
        int end = detail.indexOf('>', start);
        String origin = detail.substring(start, end);
        int parameters = origin.indexOf('(');
        return parameters < 0 ? origin : origin.substring(0, parameters);
    }
}
