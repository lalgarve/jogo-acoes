package dev.leilaalgarve.jogoacoes.login;

import dev.leilaalgarve.jogoacoes.link.LinkHandler;
import dev.leilaalgarve.jogoacoes.link.LinkOutcome;
import dev.leilaalgarve.jogoacoes.link.dto.LinkPayload;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/** Standalone login link (no competition attached) — always a single phase, never {@code complete()}. */
@Component
public class LoginLinkHandler implements LinkHandler {

    public static final String KEY = "login";

    private final UserRoleRepository userRoleRepository;

    public LoginLinkHandler(UserRoleRepository userRoleRepository) {
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public LinkOutcome consume(LinkPayload payload) {
        if (payload.userId() == null) {
            throw new IllegalArgumentException("A standalone login link must always carry a userId");
        }
        return redirectFor(payload.userId(), payload);
    }

    @Override
    public LinkOutcome alreadyAuthenticated(Long authenticatedUserId, LinkPayload payload) {
        return redirectFor(authenticatedUserId, payload);
    }

    /**
     * Spec 05-005: an already-validated {@code returnTo} carried in {@code payload.extra()}
     * (see {@code ReturnToValidator}/{@code LoginController.requestLoginLink}) wins over the
     * role-based default.
     */
    private LinkOutcome redirectFor(Long userId, LinkPayload payload) {
        String returnTo = payload.extra().get("returnTo");
        Map<String, String> redirectData = new HashMap<>();
        redirectData.put("redirectTo", returnTo != null ? returnTo : defaultDestination(userId));
        return LinkOutcome.authenticated(userId, redirectData);
    }

    private String defaultDestination(Long userId) {
        boolean administrator = hasRole(userId, RoleName.ADMINISTRATOR);
        return administrator ? "/admin" : "/competitions/mine";
    }

    private boolean hasRole(Long userId, String roleName) {
        return userRoleRepository.findByUser_Id(userId).stream()
                .anyMatch(userRole -> userRole.getRole().getName().equals(roleName));
    }
}
