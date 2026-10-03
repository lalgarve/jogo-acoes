package dev.leilaalgarve.jogoacoes.loginsession;

import dev.leilaalgarve.jogoacoes.user.RoleName;
import dev.leilaalgarve.jogoacoes.user.User;
import dev.leilaalgarve.jogoacoes.user.UserService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * "Who is logged in on this request" for every module (spec 05-029). Lives in `loginsession`
 * because this module is the one that decides the {@link Authentication}'s name is the user's
 * e-mail ({@link LoginLinkSessionService#establish}) -- no other module should need to know that.
 */
@Service
public class CurrentUserService {

    private final UserService userService;

    public CurrentUserService(UserService userService) {
        this.userService = userService;
    }

    /** For authenticated routes only: no logged-in user there is a bug, hence {@link IllegalStateException}. */
    public User currentUser() {
        Authentication auth = authenticated()
                .orElseThrow(() -> new IllegalStateException("No authenticated user on this request"));
        return userService.findByEmail(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + auth.getName()));
    }

    /** Empty when nobody is authenticated on the current request/device. */
    public Optional<Long> currentUserId() {
        return authenticated()
                .flatMap(auth -> userService.findByEmail(auth.getName()))
                .map(User::getId);
    }

    public boolean currentUserIsAdministrator() {
        return authenticated()
                .map(auth -> auth.getAuthorities().stream()
                        .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + RoleName.ADMINISTRATOR)))
                .orElse(false);
    }

    private static Optional<Authentication> authenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.of(auth);
    }
}
