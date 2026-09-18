package dev.leilaalgarve.jogoacoes.login;

import dev.leilaalgarve.jogoacoes.link.LinkRecord;
import dev.leilaalgarve.jogoacoes.link.LinkRecordRepository;
import dev.leilaalgarve.jogoacoes.link.LinkSessionService;
import dev.leilaalgarve.jogoacoes.link.LoginSession;
import dev.leilaalgarve.jogoacoes.link.LoginSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
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
 * The `login` side of the session-establishment abstraction `link` depends on (see
 * {@link LinkSessionService}): identical mechanics regardless of which {@code LinkHandler}
 * authenticated the user, but it needs {@code User}/roles to build an {@code Authentication} —
 * so it lives here, not in `link`, and is allowed to depend back on `link`'s
 * {@code LoginSession}/{@code LoginSessionRepository}.
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

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final LoginSessionRepository loginSessionRepository;
    private final LinkRecordRepository linkRecordRepository;
    private final SecurityContextRepository securityContextRepository;
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final int maxDevicesPerUser;

    public LoginLinkSessionService(UserRepository userRepository, UserRoleRepository userRoleRepository,
                                    LoginSessionRepository loginSessionRepository, LinkRecordRepository linkRecordRepository,
                                    SecurityContextRepository securityContextRepository,
                                    HttpServletRequest request, HttpServletResponse response,
                                    @Value("${login.max-devices-per-user}") int maxDevicesPerUser) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
        this.loginSessionRepository = loginSessionRepository;
        this.linkRecordRepository = linkRecordRepository;
        this.securityContextRepository = securityContextRepository;
        this.request = request;
        this.response = response;
        this.maxDevicesPerUser = maxDevicesPerUser;
    }

    @Override
    public Optional<Long> currentAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return userRepository.findByEmail(auth.getName()).map(User::getId);
    }

    @Override
    public void establish(Long userId, String token) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found: " + userId));
        LinkRecord linkRecord = linkRecordRepository.findByToken(token)
                .orElseThrow(() -> new IllegalStateException("Link record not found for token: " + token));

        enforceDeviceLimit(user);

        List<UserRole> roles = userRoleRepository.findByUser_Id(user.getId());
        List<GrantedAuthority> authorities = roles.stream()
                .map(userRole -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + userRole.getRole().getName()))
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
