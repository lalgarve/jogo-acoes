package dev.leilaalgarve.jogoacoes.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Route-ownership invariants (spec 05-006), checked against the routes Spring MVC actually
 * resolved -- not a convention read from outside. Handler methods outside the project's base
 * package (e.g. Spring Boot's own BasicErrorController, mapping "/error") are ignored.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class RouteOwnershipTest {

    private static final String BASE_PACKAGE = "dev.leilaalgarve.jogoacoes";

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    void noModuleMapsTheRootPath() {
        Set<String> rootMappers = new TreeSet<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : ourHandlerMethods().entrySet()) {
            if (entry.getKey().getPatternValues().contains("/")) {
                rootMappers.add(entry.getValue().getBeanType().getName());
            }
        }

        assertThat(rootMappers).as("classes mapping the root path \"/\"").isEmpty();
    }

    @Test
    void eachFirstPathSegmentBelongsToExactlyOneModule() {
        Map<String, Set<String>> modulesBySegment = new TreeMap<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : ourHandlerMethods().entrySet()) {
            String module = moduleOf(entry.getValue().getBeanType().getPackageName());
            for (String pattern : entry.getKey().getPatternValues()) {
                if (pattern.equals("/")) {
                    continue;
                }
                String segment = firstSegmentOf(pattern);
                modulesBySegment.computeIfAbsent(segment, key -> new TreeSet<>()).add(module);
            }
        }

        Map<String, Set<String>> conflicts = new TreeMap<>();
        modulesBySegment.forEach((segment, modules) -> {
            if (modules.size() > 1) {
                conflicts.put(segment, modules);
            }
        });

        assertThat(conflicts).as("path segments mapped by more than one module").isEmpty();
    }

    private Map<RequestMappingInfo, HandlerMethod> ourHandlerMethods() {
        Map<RequestMappingInfo, HandlerMethod> all = requestMappingHandlerMapping.getHandlerMethods();
        Map<RequestMappingInfo, HandlerMethod> ours = new TreeMap<>((a, b) -> a.toString().compareTo(b.toString()));
        all.forEach((info, handlerMethod) -> {
            if (handlerMethod.getBeanType().getPackageName().startsWith(BASE_PACKAGE)) {
                ours.put(info, handlerMethod);
            }
        });
        return ours;
    }

    private static String moduleOf(String packageName) {
        String suffix = packageName.substring(BASE_PACKAGE.length() + 1);
        int dot = suffix.indexOf('.');
        return dot == -1 ? suffix : suffix.substring(0, dot);
    }

    private static String firstSegmentOf(String pattern) {
        String withoutLeadingSlash = pattern.startsWith("/") ? pattern.substring(1) : pattern;
        int slash = withoutLeadingSlash.indexOf('/');
        return slash == -1 ? withoutLeadingSlash : withoutLeadingSlash.substring(0, slash);
    }
}
