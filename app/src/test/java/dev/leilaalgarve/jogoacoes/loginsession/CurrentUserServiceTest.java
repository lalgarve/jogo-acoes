package dev.leilaalgarve.jogoacoes.loginsession;

import dev.leilaalgarve.jogoacoes.user.RoleName;
import dev.leilaalgarve.jogoacoes.user.User;
import dev.leilaalgarve.jogoacoes.user.UserProvisioningService;
import dev.leilaalgarve.jogoacoes.user.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static dev.leilaalgarve.jogoacoes.common.testsupport.TestEmails.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Real PostgreSQL, same setup as LogRepositoryTest (specs/05-028-testes-exigem-docker-real).
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest
@Import({CurrentUserService.class, UserService.class, UserProvisioningService.class})
class CurrentUserServiceTest {

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private UserProvisioningService userProvisioningService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousRequestHasNoCurrentUser() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(currentUserService.currentUserId()).isEmpty();
        assertThat(currentUserService.currentUserIsAdministrator()).isFalse();
        assertThatThrownBy(() -> currentUserService.currentUser()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void noAuthenticationAtAllHasNoCurrentUser() {
        assertThat(currentUserService.currentUserId()).isEmpty();
        assertThat(currentUserService.currentUserIsAdministrator()).isFalse();
        assertThatThrownBy(() -> currentUserService.currentUser()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void authenticatedPlayerIsResolvedByTheEmailInTheAuthentication() {
        User player = userProvisioningService.createUser(unique("player"), "Player", List.of(RoleName.PLAYER));
        authenticateAs(player, RoleName.PLAYER);

        assertThat(currentUserService.currentUser()).isEqualTo(player);
        assertThat(currentUserService.currentUserId()).contains(player.getId());
        assertThat(currentUserService.currentUserIsAdministrator()).isFalse();
    }

    @Test
    void authenticatedAdministratorIsRecognizedAsSuch() {
        User admin = userProvisioningService.createUser(unique("admin"), "Admin", List.of(RoleName.ADMINISTRATOR));
        authenticateAs(admin, RoleName.ADMINISTRATOR);

        assertThat(currentUserService.currentUser()).isEqualTo(admin);
        assertThat(currentUserService.currentUserIsAdministrator()).isTrue();
    }

    // Same shape LoginLinkSessionService.establish() builds: name = e-mail, authorities = ROLE_*.
    private static void authenticateAs(User user, String roleName) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, AuthorityUtils.createAuthorityList("ROLE_" + roleName)));
    }
}
