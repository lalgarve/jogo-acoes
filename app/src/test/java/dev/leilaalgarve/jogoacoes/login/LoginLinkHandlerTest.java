package dev.leilaalgarve.jogoacoes.login;

import dev.leilaalgarve.jogoacoes.link.LinkOutcome;
import dev.leilaalgarve.jogoacoes.link.dto.LinkPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** The dedicated verification test spec 05-003 requires for every LinkHandler implementation. */
@ExtendWith(MockitoExtension.class)
class LoginLinkHandlerTest {

    @Mock
    private UserRoleRepository userRoleRepository;

    @Test
    void keyIsLogin() {
        assertThat(new LoginLinkHandler(userRoleRepository).key()).isEqualTo(LoginLinkHandler.KEY);
    }

    @Test
    void consumeRedirectsARegisteredPlayerToTheCompetitionsList() {
        when(userRoleRepository.findByUser_Id(1L)).thenReturn(List.of(userRoleWithRole(RoleName.PLAYER)));
        LoginLinkHandler handler = new LoginLinkHandler(userRoleRepository);

        LinkOutcome outcome = handler.consume(new LinkPayload(1L, "player@example.com", Map.of()));

        assertThat(outcome.isPending()).isFalse();
        assertThat(outcome.userId()).isEqualTo(1L);
        assertThat(outcome.redirectData()).containsEntry("redirectTo", "competitions-list");
    }

    @Test
    void consumeRedirectsAnAdministratorToTheAdminPage() {
        when(userRoleRepository.findByUser_Id(2L)).thenReturn(List.of(userRoleWithRole(RoleName.ADMINISTRATOR)));
        LoginLinkHandler handler = new LoginLinkHandler(userRoleRepository);

        LinkOutcome outcome = handler.consume(new LinkPayload(2L, "admin@example.com", Map.of()));

        assertThat(outcome.redirectData()).containsEntry("redirectTo", "admin-page");
    }

    @Test
    void alreadyAuthenticatedRedirectsUsingTheCurrentSessionUserNotThePayloadsOwner() {
        when(userRoleRepository.findByUser_Id(2L)).thenReturn(List.of(userRoleWithRole(RoleName.ADMINISTRATOR)));
        LoginLinkHandler handler = new LoginLinkHandler(userRoleRepository);

        // payload targets user 1 (a plain player), but user 2 (an administrator) is already
        // authenticated on this device -- the redirect must reflect user 2, per login.feature.
        LinkOutcome outcome = handler.alreadyAuthenticated(2L, new LinkPayload(1L, "player@example.com", Map.of()));

        assertThat(outcome.userId()).isEqualTo(2L);
        assertThat(outcome.redirectData()).containsEntry("redirectTo", "admin-page");
    }

    @Test
    void consumeRejectsAPayloadWithoutAUserId() {
        LoginLinkHandler handler = new LoginLinkHandler(userRoleRepository);

        assertThatThrownBy(() -> handler.consume(new LinkPayload(null, "someone@example.com", Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private UserRole userRoleWithRole(String roleName) {
        Role role = new Role();
        role.setName(roleName);
        UserRole userRole = new UserRole();
        userRole.setRole(role);
        userRole.setAssignedAt(LocalDateTime.now());
        return userRole;
    }
}
