package dev.leilaalgarve.jogoacoes.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 05-061: the cycle search on graphs drawn by hand, without planting cycles in app. */
class ModuleDependencyGraphTest {

    @Test
    void graphWithoutCycleHasNone() {
        ModuleDependencyGraph graph = new ModuleDependencyGraph();
        graph.addEdge("competition", "email", "Competition -> Email");
        graph.addEdge("loginsession", "email", "Login -> Email");
        graph.addEdge("email", "user", "Email -> User");

        assertThat(graph.cycles()).isEmpty();
    }

    @Test
    void findsACycleOfTwoModules() {
        ModuleDependencyGraph graph = new ModuleDependencyGraph();
        graph.addEdge("log", "user", "AuditLogService -> User");
        graph.addEdge("user", "log", "UserService -> AuditLogService");

        List<List<String>> cycles = graph.cycles();

        assertThat(cycles).containsExactly(List.of("log", "user", "log"));
        assertThat(graph.describe(cycles))
                .contains("log -> user -> log")
                .contains("log -> user: AuditLogService -> User")
                .contains("user -> log: UserService -> AuditLogService");
    }

    @Test
    void findsACycleOfThreeModules() {
        ModuleDependencyGraph graph = new ModuleDependencyGraph();
        graph.addEdge("competition", "email", "a");
        graph.addEdge("email", "loginsession", "b");
        graph.addEdge("loginsession", "competition", "c");
        graph.addEdge("loginsession", "user", "d");

        assertThat(graph.cycles()).containsExactly(List.of("competition", "email", "loginsession", "competition"));
    }

    @Test
    void reportsEveryTangledGroupNotOnlyTheFirst() {
        ModuleDependencyGraph graph = new ModuleDependencyGraph();
        graph.addEdge("a", "b", "x");
        graph.addEdge("b", "a", "x");
        graph.addEdge("c", "d", "x");
        graph.addEdge("d", "c", "x");
        graph.addEdge("b", "c", "x");

        assertThat(graph.cycles()).containsExactlyInAnyOrder(List.of("a", "b", "a"), List.of("c", "d", "c"));
    }

    @Test
    void referenceInsideTheSameModuleIsNotAnEdge() {
        ModuleDependencyGraph graph = new ModuleDependencyGraph();
        graph.addEdge("competition", "competition", "CompetitionService -> CompetitionNotFoundException");

        assertThat(graph.cycles()).isEmpty();
    }
}
