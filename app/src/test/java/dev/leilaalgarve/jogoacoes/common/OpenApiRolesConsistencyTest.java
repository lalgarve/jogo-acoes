package dev.leilaalgarve.jogoacoes.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.access.WebInvocationPrivilegeEvaluator;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-007: compares each operation's {@code x-roles} in docs/openapi.yaml against the real
 * authorization decision ({@link WebInvocationPrivilegeEvaluator}, auto-registered by
 * {@code @EnableWebSecurity} for a single SecurityFilterChain -- no manual construction
 * needed). {@code x-roles: []} means everyone, including anonymous; a non-empty list is read
 * literally as exactly who is allowed -- no special-casing, so a role combination the
 * SecurityConfigContributors can't express (e.g. PLAYER alone, never used today) would
 * correctly fail here instead of being silently accepted.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class OpenApiRolesConsistencyTest {

    private static final Path OPENAPI_YAML = Path.of("../docs/openapi.yaml");
    private static final Set<String> HTTP_METHOD_KEYS =
            Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");
    private static final String DUMMY_PATH_VARIABLE_VALUE = "999999";

    private static final Authentication ANONYMOUS =
            new AnonymousAuthenticationToken("openapi-roles-test", "anonymousUser",
                    AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    private static final Authentication PLAYER = new TestingAuthenticationToken("player", "n/a", "ROLE_PLAYER");
    private static final Authentication ADMINISTRATOR =
            new TestingAuthenticationToken("administrator", "n/a", "ROLE_ADMINISTRATOR");

    @Autowired
    private WebInvocationPrivilegeEvaluator privilegeEvaluator;

    @Test
    void everyOperationsXRolesMatchesTheRealAuthorizationDecision() throws IOException {
        List<String> failures = new ArrayList<>();

        for (Operation operation : operationsFromOpenApiContract()) {
            String uri = operation.path().replaceAll("\\{[^}]+}", DUMMY_PATH_VARIABLE_VALUE);
            check(operation, "ANONYMOUS", uri, ANONYMOUS, operation.xRoles().isEmpty(), failures);
            check(operation, "PLAYER", uri, PLAYER,
                    operation.xRoles().isEmpty() || operation.xRoles().contains("PLAYER"), failures);
            check(operation, "ADMINISTRATOR", uri, ADMINISTRATOR,
                    operation.xRoles().isEmpty() || operation.xRoles().contains("ADMINISTRATOR"), failures);
        }

        assertThat(failures)
                .as("x-roles vs. the real authorization decision (WebInvocationPrivilegeEvaluator)")
                .isEmpty();
    }

    private void check(Operation operation, String roleLabel, String uri, Authentication authentication,
                        boolean expectedAllowed, List<String> failures) {
        boolean actualAllowed = privilegeEvaluator.isAllowed("", uri, operation.method(), authentication);
        if (actualAllowed != expectedAllowed) {
            failures.add(String.format("%s %s (x-roles=%s): %s expected allowed=%s but was %s",
                    operation.method(), operation.path(), operation.xRoles(), roleLabel, expectedAllowed, actualAllowed));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Operation> operationsFromOpenApiContract() throws IOException {
        List<Operation> operations = new ArrayList<>();
        try (InputStream in = Files.newInputStream(OPENAPI_YAML)) {
            Map<String, Object> spec = new Yaml().load(in);
            Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
            for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
                String path = pathEntry.getKey();
                Map<String, Object> pathItem = (Map<String, Object>) pathEntry.getValue();
                for (Map.Entry<String, Object> methodEntry : pathItem.entrySet()) {
                    if (!HTTP_METHOD_KEYS.contains(methodEntry.getKey()) || !(methodEntry.getValue() instanceof Map)) {
                        continue;
                    }
                    Map<String, Object> op = (Map<String, Object>) methodEntry.getValue();
                    List<String> xRoles = (List<String>) op.getOrDefault("x-roles", List.of());
                    operations.add(new Operation(methodEntry.getKey().toUpperCase(), path, xRoles));
                }
            }
        }
        return operations;
    }

    private record Operation(String method, String path, List<String> xRoles) {
    }
}
