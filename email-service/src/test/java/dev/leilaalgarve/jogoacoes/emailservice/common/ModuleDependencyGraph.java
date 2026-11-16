package dev.leilaalgarve.jogoacoes.emailservice.common;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Spec 05-061: a directed graph where each node is a module (the first package below the base
 * package) and an edge A -> B says some class of A references some class of B. Cycles are found
 * with Tarjan's strongly connected components: every component with more than one module holds
 * at least one cycle, and a depth-first search inside it picks one concrete cycle to report.
 *
 * <p>Duplicated from app on purpose: the two services share no Java code, not even in
 * tests (spec 05-034).
 */
final class ModuleDependencyGraph {

    /** from -> (to -> one example reference, "Origin -> Target"). Sorted, so reports are stable. */
    private final Map<String, Map<String, String>> edges = new TreeMap<>();

    static ModuleDependencyGraph of(JavaClasses classes, String basePackage) {
        ModuleDependencyGraph graph = new ModuleDependencyGraph();
        for (JavaClass origin : classes) {
            Optional<String> from = moduleOf(origin, basePackage);
            if (from.isEmpty()) {
                continue;
            }
            graph.addModule(from.get());
            for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass().getBaseComponentType();
                moduleOf(target, basePackage)
                        .filter(to -> !to.equals(from.get()))
                        .ifPresent(to -> graph.addEdge(from.get(), to,
                                origin.getName() + " -> " + target.getName()));
            }
        }
        return graph;
    }

    void addModule(String module) {
        edges.computeIfAbsent(module, m -> new TreeMap<>());
    }

    /** Keeps the first example seen for an edge; a self-reference is not an edge. */
    void addEdge(String from, String to, String example) {
        if (from.equals(to)) {
            return;
        }
        addModule(from);
        addModule(to);
        edges.get(from).putIfAbsent(to, example);
    }

    /** One concrete cycle per tangled component, each as [A, B, ..., A]; empty when there is none. */
    List<List<String>> cycles() {
        List<List<String>> cycles = new ArrayList<>();
        for (Set<String> component : tangledComponents()) {
            cycles.add(cycleWithin(component));
        }
        return cycles;
    }

    /** Every cycle with the example reference of each of its edges, for the failure message. */
    String describe(List<List<String>> cycles) {
        StringBuilder report = new StringBuilder();
        for (List<String> cycle : cycles) {
            report.append(System.lineSeparator()).append(String.join(" -> ", cycle));
            for (int i = 0; i + 1 < cycle.size(); i++) {
                String from = cycle.get(i);
                String to = cycle.get(i + 1);
                report.append(System.lineSeparator()).append("    ").append(from).append(" -> ").append(to)
                        .append(": ").append(edges.get(from).get(to));
            }
        }
        return report.toString();
    }

    /** Tarjan's algorithm; only components with more than one module are returned. */
    private List<Set<String>> tangledComponents() {
        Tarjan tarjan = new Tarjan();
        for (String module : edges.keySet()) {
            if (!tarjan.index.containsKey(module)) {
                tarjan.visit(module);
            }
        }
        return tarjan.components.stream().filter(component -> component.size() > 1).toList();
    }

    /**
     * Depth-first search from the component's first module, staying inside the component, until
     * an edge closes back on a module on the current path.
     */
    private List<String> cycleWithin(Set<String> component) {
        String start = component.iterator().next();
        Deque<String> path = new ArrayDeque<>();
        List<String> cycle = search(start, component, path, new TreeSet<>());
        if (cycle == null) {
            throw new IllegalStateException("No cycle found in strongly connected component " + component);
        }
        return cycle;
    }

    private List<String> search(String module, Set<String> component, Deque<String> path, Set<String> done) {
        path.addLast(module);
        for (String next : edges.get(module).keySet()) {
            if (!component.contains(next) || done.contains(next)) {
                continue;
            }
            if (path.contains(next)) {
                List<String> cycle = new ArrayList<>();
                boolean inCycle = false;
                for (String onPath : path) {
                    inCycle = inCycle || onPath.equals(next);
                    if (inCycle) {
                        cycle.add(onPath);
                    }
                }
                cycle.add(next);
                return cycle;
            }
            List<String> found = search(next, component, path, done);
            if (found != null) {
                return found;
            }
        }
        path.removeLast();
        done.add(module);
        return null;
    }

    private final class Tarjan {
        private final Map<String, Integer> index = new HashMap<>();
        private final Map<String, Integer> lowLink = new HashMap<>();
        private final Deque<String> stack = new ArrayDeque<>();
        private final List<Set<String>> components = new ArrayList<>();
        private int counter;

        private void visit(String module) {
            index.put(module, counter);
            lowLink.put(module, counter);
            counter++;
            stack.push(module);
            for (String next : edges.get(module).keySet()) {
                if (!index.containsKey(next)) {
                    visit(next);
                    lowLink.put(module, Math.min(lowLink.get(module), lowLink.get(next)));
                } else if (stack.contains(next)) {
                    lowLink.put(module, Math.min(lowLink.get(module), index.get(next)));
                }
            }
            if (lowLink.get(module).equals(index.get(module))) {
                Set<String> component = new TreeSet<>();
                String member;
                do {
                    member = stack.pop();
                    component.add(member);
                } while (!member.equals(module));
                components.add(component);
            }
        }
    }

    /**
     * First package segment below the base package ({@code competition.exception} is
     * {@code competition}); empty for classes outside it or right in it, like the application class.
     */
    private static Optional<String> moduleOf(JavaClass javaClass, String basePackage) {
        String packageName = javaClass.getPackageName();
        if (!packageName.startsWith(basePackage + ".")) {
            return Optional.empty();
        }
        String relative = packageName.substring(basePackage.length() + 1);
        int dot = relative.indexOf('.');
        return Optional.of(dot < 0 ? relative : relative.substring(0, dot));
    }
}
