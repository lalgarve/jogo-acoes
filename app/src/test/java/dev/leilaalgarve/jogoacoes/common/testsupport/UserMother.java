package dev.leilaalgarve.jogoacoes.common.testsupport;

import dev.leilaalgarve.jogoacoes.login.Role;
import dev.leilaalgarve.jogoacoes.login.RoleName;
import dev.leilaalgarve.jogoacoes.login.User;
import dev.leilaalgarve.jogoacoes.login.UserRole;
import dev.leilaalgarve.jogoacoes.login.UserRoleId;
import dev.leilaalgarve.jogoacoes.login.RoleRepository;
import dev.leilaalgarve.jogoacoes.login.UserRepository;
import dev.leilaalgarve.jogoacoes.login.UserRoleRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persists test users with a role already assigned, so step definitions don't repeat the
 * user/role/user_role wiring for every scenario that needs "the administrator" or "a
 * registered player".
 */
@Component
public class UserMother {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    public UserMother(UserRepository userRepository, RoleRepository roleRepository, UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
    }

    public User administrator() {
        return registeredUserWithRole("admin", RoleName.ADMINISTRATOR);
    }

    public User registeredPlayer() {
        return registeredUserWithRole("player", RoleName.PLAYER);
    }

    private User registeredUserWithRole(String emailLocalPart, String roleName) {
        User user = new User();
        user.setName(emailLocalPart + " test user");
        user.setEmail(emailLocalPart + "-" + UUID.randomUUID() + "@example.com");
        user.setRegistered(true);
        user = userRepository.save(user);

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + roleName));

        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(user.getId(), role.getId()));
        userRole.setUser(user);
        userRole.setRole(role);
        userRole.setAssignedAt(LocalDateTime.now());
        userRoleRepository.save(userRole);

        return user;
    }
}
