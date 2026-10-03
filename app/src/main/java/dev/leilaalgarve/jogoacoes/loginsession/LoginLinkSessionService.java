package dev.leilaalgarve.jogoacoes.loginsession;

import dev.leilaalgarve.jogoacoes.link.LinkRecord;
import dev.leilaalgarve.jogoacoes.link.LinkSessionService;
import dev.leilaalgarve.jogoacoes.user.User;
import dev.leilaalgarve.jogoacoes.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * The `loginsession` side of the session-establishment abstraction `link` depends on (see
 * {@link LinkSessionService}): identical mechanics regardless of which {@code LinkHandler}
 * authenticated the user, but it needs {@code User}/roles to build an {@code Authentication} —
 * so it lives here, not in `link`, and is allowed to depend back on `link` -- through its
 * service/interface types only, never its repository (spec 05-029).
 *
 * <p>Device identity is never compared explicitly -- there's no frontend yet to carry a device
 * id, and it turns out not to be needed: "is this device already authenticated" (checked one
 * level up, in {@code LinkService}) plus "has this link already been used" is enough to
 * implement every device-related rule in login.feature. LOGIN_SESSION's device_id column is
 * filled by {@link DeviceLabelResolver} (User-Agent Client Hints when present, spec 05-009)
 * purely as a human-readable label, per der.md's "device name to show the player" -- it plays no
 * role in any decision here.
 */
@Component
public class LoginLinkSessionService implements LinkSessionService {

    private final UserService userService;
    private final CurrentUserService currentUserService;
    private final LoginSessionRepository loginSessionRepository;
    private final SecurityContextRepository securityContextRepository;
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final int maxDevicesPerUser;

    public LoginLinkSessionService(UserService userService, CurrentUserService currentUserService,
                                    LoginSessionRepository loginSessionRepository,
                                    SecurityContextRepository securityContextRepository,
                                    HttpServletRequest request, HttpServletResponse response,
                                    @Value("${login.max-devices-per-user}") int maxDevicesPerUser) {
        this.userService = userService;
        this.currentUserService = currentUserService;
        this.loginSessionRepository = loginSessionRepository;
        this.securityContextRepository = securityContextRepository;
        this.request = request;
        this.response = response;
        this.maxDevicesPerUser = maxDevicesPerUser;
    }

    @Override
    public Optional<Long> currentAuthenticatedUserId() {
        return currentUserService.currentUserId();
    }

    @Override
    public void establish(Long userId, LinkRecord linkRecord) {
        User user = userService.getById(userId);

        enforceDeviceLimit(user);

        List<GrantedAuthority> authorities = userService.roleNamesOf(user.getId()).stream()
                .map(roleName -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + roleName))
                .toList();

        Authentication authentication = new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        LoginSession session = new LoginSession();
        session.setUserId(user.getId());
        session.setLinkRecord(linkRecord);
        session.setDeviceId(deviceLabel());
        // Read after saveContext() above, which is what guarantees the HTTP session already
        // exists/persisted (spec 05-010) -- this is what makes revoking a LOGIN_SESSION later
        // (SessionsController) have a real effect via SessionRepository.deleteById(...).
        session.setHttpSessionId(request.getSession().getId());
        session.setCreatedAt(LocalDateTime.now());
        loginSessionRepository.save(session);
    }

    private void enforceDeviceLimit(User user) {
        List<LoginSession> active = loginSessionRepository.findByUserIdAndEndedAtIsNullOrderByCreatedAtAsc(user.getId());
        if (active.size() >= maxDevicesPerUser) {
            LoginSession oldest = active.get(0);
            oldest.setEndedAt(LocalDateTime.now());
            loginSessionRepository.save(oldest);
        }
    }

    private String deviceLabel() {
        return DeviceLabelResolver.resolve(
                request.getHeader("Sec-CH-UA"),
                request.getHeader("Sec-CH-UA-Platform"),
                request.getHeader("Sec-CH-UA-Platform-Version"),
                request.getHeader("Sec-CH-UA-Mobile"),
                request.getHeader("User-Agent"));
    }
}
