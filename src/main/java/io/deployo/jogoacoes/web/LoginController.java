package io.deployo.jogoacoes.web;

import io.deployo.jogoacoes.api.LoginApi;
import io.deployo.jogoacoes.api.model.LoginResult;
import io.deployo.jogoacoes.domain.LoginLink;
import io.deployo.jogoacoes.domain.RoleName;
import io.deployo.jogoacoes.domain.User;
import io.deployo.jogoacoes.domain.UserRole;
import io.deployo.jogoacoes.repository.LoginLinkRepository;
import io.deployo.jogoacoes.repository.UserRoleRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Only the happy path of consumeLoginLink is implemented here (an unused, unexpired link
 * for an already-registered user). login.feature's device rules (link-per-device,
 * invalidation, session limits) and new-player registration are a separate pass -- this is
 * enough for other .feature files' "the user is logged into the system" step to establish a
 * real session through the real HTTP endpoint, rather than a test-only shortcut.
 */
@RestController
public class LoginController implements LoginApi {

    private final LoginLinkRepository loginLinkRepository;
    private final UserRoleRepository userRoleRepository;
    private final SecurityContextRepository securityContextRepository;
    private final HttpServletRequest request;
    private final HttpServletResponse response;

    public LoginController(LoginLinkRepository loginLinkRepository, UserRoleRepository userRoleRepository,
                            SecurityContextRepository securityContextRepository, HttpServletRequest request,
                            HttpServletResponse response) {
        this.loginLinkRepository = loginLinkRepository;
        this.userRoleRepository = userRoleRepository;
        this.securityContextRepository = securityContextRepository;
        this.request = request;
        this.response = response;
    }

    @Override
    public ResponseEntity<LoginResult> consumeLoginLink(String token) {
        LoginLink link = loginLinkRepository.findByToken(token)
                .orElseThrow(() -> new LoginLinkInvalidException("Link invalid or expired"));
        if (link.getUsedAt() != null || link.getInvalidatedAt() != null
                || link.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new LoginLinkInvalidException("Link invalid or expired");
        }

        User user = link.getUser();
        if (user == null && link.getParticipation() != null) {
            user = link.getParticipation().getUser();
        }
        if (user == null) {
            // New player, registration not completed yet -- out of scope for this pass.
            return ResponseEntity.status(202).build();
        }

        link.setUsedAt(LocalDateTime.now());
        loginLinkRepository.save(link);

        boolean isAdministrator = establishSession(user);

        LoginResult result = new LoginResult();
        if (isAdministrator) {
            result.setRedirectTo(LoginResult.RedirectToEnum.ADMIN_PAGE);
        } else if (link.getParticipation() != null) {
            result.setRedirectTo(LoginResult.RedirectToEnum.COMPETITION_PAGE);
            result.competitionId(link.getParticipation().getCompetition().getId());
        } else {
            result.setRedirectTo(LoginResult.RedirectToEnum.COMPETITIONS_LIST);
        }
        return ResponseEntity.ok(result);
    }

    private boolean establishSession(User user) {
        List<UserRole> roles = userRoleRepository.findByUser_Id(user.getId());
        List<GrantedAuthority> authorities = roles.stream()
                .map(ur -> new SimpleGrantedAuthority("ROLE_" + ur.getRole().getName()))
                .map(GrantedAuthority.class::cast)
                .toList();

        var authentication = new UsernamePasswordAuthenticationToken(user.getEmail(), null, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return authorities.contains(new SimpleGrantedAuthority("ROLE_" + RoleName.ADMINISTRATOR));
    }
}
