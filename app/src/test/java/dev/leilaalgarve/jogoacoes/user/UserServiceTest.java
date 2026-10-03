package dev.leilaalgarve.jogoacoes.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.util.List;

import static dev.leilaalgarve.jogoacoes.common.testsupport.TestEmails.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Real PostgreSQL, same setup as LogRepositoryTest (specs/05-028-testes-exigem-docker-real).
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest
@Import({UserService.class, UserProvisioningService.class})
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserProvisioningService userProvisioningService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByEmailReturnsTheUserWhetherOrNotRegistered() {
        User unregistered = unregisteredUser(unique("pending"));

        assertThat(userService.findByEmail(unregistered.getEmail())).contains(unregistered);
        assertThat(userService.findByEmail(unique("nobody"))).isEmpty();
    }

    @Test
    void findRegisteredByEmailIgnoresUsersWhoHaveNotFinishedRegistration() {
        User registered = userProvisioningService.createUser(unique("player"), "Player", List.of(RoleName.PLAYER));
        User unregistered = unregisteredUser(unique("pending"));

        assertThat(userService.findRegisteredByEmail(registered.getEmail())).contains(registered);
        assertThat(userService.findRegisteredByEmail(unregistered.getEmail())).isEmpty();
    }

    @Test
    void getByIdReturnsTheUserOrThrowsWhenItDoesNotExist() {
        User user = userProvisioningService.createUser(unique("player"), "Player", List.of(RoleName.PLAYER));

        assertThat(userService.getById(user.getId())).isEqualTo(user);
        assertThatThrownBy(() -> userService.getById(Long.MAX_VALUE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void roleNamesOfAndHasRoleReflectEveryRoleTheUserHolds() {
        User both = userProvisioningService.createUser(unique("both"), "Both",
                List.of(RoleName.ADMINISTRATOR, RoleName.PLAYER));
        User player = userProvisioningService.createUser(unique("player"), "Player", List.of(RoleName.PLAYER));

        assertThat(userService.roleNamesOf(both.getId()))
                .containsExactlyInAnyOrder(RoleName.ADMINISTRATOR, RoleName.PLAYER);
        assertThat(userService.hasRole(both.getId(), RoleName.ADMINISTRATOR)).isTrue();
        assertThat(userService.hasRole(player.getId(), RoleName.ADMINISTRATOR)).isFalse();
        assertThat(userService.hasRole(player.getId(), RoleName.PLAYER)).isTrue();
    }

    private User unregisteredUser(String email) {
        User user = new User();
        user.setName("Pending");
        user.setEmail(email);
        user.setRegistered(false);
        return userRepository.save(user);
    }
}
