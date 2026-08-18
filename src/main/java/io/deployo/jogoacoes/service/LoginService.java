package io.deployo.jogoacoes.service;

import io.deployo.jogoacoes.api.model.LoginResult;
import io.deployo.jogoacoes.domain.CompetitionStatus;
import io.deployo.jogoacoes.domain.EmailTemplate;
import io.deployo.jogoacoes.domain.LoginLink;
import io.deployo.jogoacoes.domain.LoginSession;
import io.deployo.jogoacoes.domain.Participation;
import io.deployo.jogoacoes.domain.ParticipationStatus;
import io.deployo.jogoacoes.domain.Role;
import io.deployo.jogoacoes.domain.RoleName;
import io.deployo.jogoacoes.domain.User;
import io.deployo.jogoacoes.domain.UserRole;
import io.deployo.jogoacoes.domain.UserRoleId;
import io.deployo.jogoacoes.email.EmailSender;
import io.deployo.jogoacoes.repository.LoginLinkRepository;
import io.deployo.jogoacoes.repository.LoginSessionRepository;
import io.deployo.jogoacoes.repository.ParticipationRepository;
import io.deployo.jogoacoes.repository.RoleRepository;
import io.deployo.jogoacoes.repository.UserRepository;
import io.deployo.jogoacoes.repository.UserRoleRepository;
import io.deployo.jogoacoes.web.LoginLinkInvalidException;
import io.deployo.jogoacoes.web.LoginLinkUsedOnAnotherDeviceException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * login.feature beyond the bare happy path LoginController used to implement inline:
 * device rules (a link only logs in the device that first used it; already being logged in
 * on this device short-circuits straight to a redirect regardless of the link's own state;
 * a fresh link invalidates the previous one; a device-count limit ends the oldest session)
 * and new-player registration.
 *
 * Device identity is never compared explicitly -- there's no frontend yet to carry a device
 * id, and it turns out not to be needed: "is this device already authenticated" plus
 * "has this link already been used (by definition, elsewhere)" is enough to implement every
 * device-related rule in the .feature file. LOGIN_SESSION's device_id column is filled from
 * the User-Agent header purely as a human-readable label, per der.md's "device name to show
 * the player" -- it plays no role in any decision here.
 */
@Service
public class LoginService {

    private static final int LINK_VALIDITY_DAYS = 7;

    private final LoginLinkRepository loginLinkRepository;
    private final LoginSessionRepository loginSessionRepository;
    private final ParticipationRepository participationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final EmailSender emailSender;
    private final SecurityContextRepository securityContextRepository;
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final int maxDevicesPerUser;

    public LoginService(LoginLinkRepository loginLinkRepository, LoginSessionRepository loginSessionRepository,
                         ParticipationRepository participationRepository, UserRepository userRepository,
                         RoleRepository roleRepository, UserRoleRepository userRoleRepository, EmailSender emailSender,
                         SecurityContextRepository securityContextRepository, HttpServletRequest request,
                         HttpServletResponse response, @Value("${login.max-devices-per-user}") int maxDevicesPerUser) {
        this.loginLinkRepository = loginLinkRepository;
        this.loginSessionRepository = loginSessionRepository;
        this.participationRepository = participationRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.emailSender = emailSender;
        this.securityContextRepository = securityContextRepository;
        this.request = request;
        this.response = response;
        this.maxDevicesPerUser = maxDevicesPerUser;
    }

    /** @return null to signal "202, new player, registration still required". */
    @Transactional
    public LoginResult consumeLoginLink(String token) {
        LoginLink link = findValidLink(token);

        if (isAuthenticated()) {
            return buildRedirect(link, currentAuthenticatedUser());
        }

        if (link.getUsedAt() != null) {
            throw new LoginLinkUsedOnAnotherDeviceException("Link already used to log in on a different device");
        }

        rejectIfClosedAndUnfinished(link);

        User user = resolveUser(link);
        if (user == null) {
            return null;
        }

        markUsedAndEstablishSession(link, user);
        return buildRedirect(link, user);
    }

    @Transactional
    public LoginResult completeRegistration(String token, String name) {
        LoginLink link = findValidLink(token);
        if (link.getUsedAt() != null) {
            throw new LoginLinkInvalidException("Link invalid or expired");
        }
        Participation participation = link.getParticipation();
        if (participation == null) {
            throw new LoginLinkInvalidException("Link invalid or expired");
        }

        User user = new User();
        user.setName(name);
        user.setEmail(link.getEmail());
        user.setRegistered(true);
        user = userRepository.save(user);
        assignRole(user, RoleName.PLAYER);

        participation.setUser(user);
        participation.setStatus(ParticipationStatus.IN_COMPETITION);
        participation.setJoinedAt(LocalDate.now());
        participationRepository.save(participation);

        markUsedAndEstablishSession(link, user);

        LoginResult result = new LoginResult();
        result.setRedirectTo(LoginResult.RedirectToEnum.COMPETITION_PAGE);
        result.competitionId(participation.getCompetition().getId());
        return result;
    }

    @Transactional
    public void requestLoginLink(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            return; // Don't reveal whether the address is known.
        }

        LocalDateTime now = LocalDateTime.now();
        for (LoginLink previous : loginLinkRepository.findByUser_IdAndUsedAtIsNullAndInvalidatedAtIsNull(user.getId())) {
            previous.setInvalidatedAt(now);
            loginLinkRepository.save(previous);
        }

        LoginLink link = new LoginLink();
        link.setToken(UUID.randomUUID().toString());
        link.setEmail(email);
        link.setUser(user);
        link.setEmailSentAt(now);
        link.setExpiresAt(now.plusDays(LINK_VALIDITY_DAYS));
        loginLinkRepository.save(link);

        emailSender.send(user.getId(), email, "/login-links/" + link.getToken(), EmailTemplate.LOGIN_LINK);
    }

    private LoginLink findValidLink(String token) {
        LoginLink link = loginLinkRepository.findByToken(token)
                .orElseThrow(() -> new LoginLinkInvalidException("Link invalid or expired"));
        if (link.getInvalidatedAt() != null || link.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new LoginLinkInvalidException("Link invalid or expired");
        }
        return link;
    }

    private void rejectIfClosedAndUnfinished(LoginLink link) {
        Participation participation = link.getParticipation();
        if (participation == null) {
            return;
        }
        boolean finished = participation.getStatus() == ParticipationStatus.IN_COMPETITION;
        if (participation.getCompetition().getStatus() == CompetitionStatus.CLOSED && !finished) {
            throw new LoginLinkInvalidException("Link invalid or expired");
        }
    }

    private User resolveUser(LoginLink link) {
        if (link.getUser() != null) {
            return link.getUser();
        }
        if (link.getParticipation() != null) {
            return link.getParticipation().getUser();
        }
        return null;
    }

    private void markUsedAndEstablishSession(LoginLink link, User user) {
        link.setUsedAt(LocalDateTime.now());
        loginLinkRepository.save(link);

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
        session.setUser(user);
        session.setLoginLink(link);
        session.setDeviceId(deviceLabel());
        session.setCreatedAt(LocalDateTime.now());
        loginSessionRepository.save(session);
    }

    private void enforceDeviceLimit(User user) {
        List<LoginSession> active = loginSessionRepository.findByUser_IdAndEndedAtIsNullOrderByCreatedAtAsc(user.getId());
        if (active.size() >= maxDevicesPerUser) {
            LoginSession oldest = active.get(0);
            oldest.setEndedAt(LocalDateTime.now());
            loginSessionRepository.save(oldest);
        }
    }

    private String deviceLabel() {
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null ? userAgent : "unknown-device";
    }

    private LoginResult buildRedirect(LoginLink link, User actingUser) {
        LoginResult result = new LoginResult();
        if (link.getParticipation() != null) {
            result.setRedirectTo(LoginResult.RedirectToEnum.COMPETITION_PAGE);
            result.competitionId(link.getParticipation().getCompetition().getId());
        } else {
            boolean administrator = actingUser != null && hasRole(actingUser, RoleName.ADMINISTRATOR);
            result.setRedirectTo(administrator ? LoginResult.RedirectToEnum.ADMIN_PAGE : LoginResult.RedirectToEnum.COMPETITIONS_LIST);
        }
        return result;
    }

    private boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }

    private User currentAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        return userRepository.findByEmail(auth.getName()).orElse(null);
    }

    private boolean hasRole(User user, String roleName) {
        return userRoleRepository.findByUser_Id(user.getId()).stream()
                .anyMatch(userRole -> userRole.getRole().getName().equals(roleName));
    }

    private void assignRole(User user, String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + roleName));
        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(user.getId(), role.getId()));
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedAt(LocalDateTime.now());
        userRoleRepository.save(userRole);
    }
}
