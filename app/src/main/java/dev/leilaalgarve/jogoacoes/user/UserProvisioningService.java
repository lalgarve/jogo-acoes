package dev.leilaalgarve.jogoacoes.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Creates a user and assigns it one or more roles in a single place -- consolidates what
 * BlackboxDataSeeder and CompetitionLinkHandler.complete() each implemented separately
 * (Issue #95). A user can hold more than one role at once (UserRole is its own association
 * table, not a single column on User), so callers always pass a list, even a single-role one.
 */
@Service
public class UserProvisioningService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    public UserProvisioningService(UserRepository userRepository, RoleRepository roleRepository,
                                    UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
    }

    /** @return {@code true} if at least one user already holds {@code roleName}. */
    public boolean existsAnyWithRole(String roleName) {
        return userRoleRepository.existsByRole_Name(roleName);
    }

    @Transactional
    public User createUser(String email, String name, List<String> roleNames) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setRegistered(true);
        user = userRepository.save(user);

        for (String roleName : roleNames) {
            assignRole(user, roleName);
        }
        return user;
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
