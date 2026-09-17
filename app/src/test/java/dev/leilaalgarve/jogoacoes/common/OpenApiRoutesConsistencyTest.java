package dev.leilaalgarve.jogoacoes.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-007: docs/openapi.yaml is the API-first source of truth -- every route Spring MVC
 * actually resolved must trace back to it, and vice versa. Correlates by (method, path) rather
 * than operationId: the openapi-generator plugin emits the OpenAPI path straight into
 * {@code @RequestMapping}, so the two sides use identical path syntax already.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class OpenApiRoutesConsistencyTest {

    private static final String BASE_PACKAGE = "dev.leilaalgarve.jogoacoes";
    private static final Path OPENAPI_YAML = Path.of("../docs/openapi.yaml");
    private static final Set<String> HTTP_METHOD_KEYS =
            Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Test
    void everyImplementedRouteIsDeclaredInTheContract() throws IOException {
        Set<String> implementedButUndocumented = new TreeSet<>(routesFromSpringMvc());
        implementedButUndocumented.removeAll(routesFromOpenApiContract());

        assertThat(implementedButUndocumented)
                .as("routes implemented outside docs/openapi.yaml (bypassing the generated contract)")
                .isEmpty();
    }

    @Test
    void everyContractOperationIsImplemented() throws IOException {
        Set<String> documentedButUnimplemented = new TreeSet<>(routesFromOpenApiContract());
        documentedButUnimplemented.removeAll(routesFromSpringMvc());

        assertThat(documentedButUnimplemented)
                .as("operations in docs/openapi.yaml with no matching Spring route")
                .isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Set<String> routesFromOpenApiContract() throws IOException {
        Set<String> routes = new TreeSet<>();
        try (InputStream in = Files.newInputStream(OPENAPI_YAML)) {
            Map<String, Object> spec = new Yaml().load(in);
            Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
            for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
                String path = pathEntry.getKey();
                Map<String, Object> pathItem = (Map<String, Object>) pathEntry.getValue();
                for (Map.Entry<String, Object> methodEntry : pathItem.entrySet()) {
                    if (HTTP_METHOD_KEYS.contains(methodEntry.getKey()) && methodEntry.getValue() instanceof Map) {
                        routes.add(methodEntry.getKey().toUpperCase() + " " + path);
                    }
                }
            }
        }
        return routes;
    }

    private Set<String> routesFromSpringMvc() {
        Set<String> routes = new TreeSet<>();
        requestMappingHandlerMapping.getHandlerMethods().forEach((info, handlerMethod) -> {
            if (!handlerMethod.getBeanType().getPackageName().startsWith(BASE_PACKAGE)) {
                return;
            }
            addRoutes(routes, info);
        });
        return routes;
    }

    private static void addRoutes(Set<String> routes, RequestMappingInfo info) {
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        for (String pattern : info.getPatternValues()) {
            for (RequestMethod method : methods) {
                routes.add(method.name() + " " + pattern);
            }
        }
    }
}
