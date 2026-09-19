package dev.leilaalgarve.jogoacoes.blackbox;

import dev.leilaalgarve.jogoacoes.login.Role;
import dev.leilaalgarve.jogoacoes.login.RoleName;
import dev.leilaalgarve.jogoacoes.login.RoleRepository;
import dev.leilaalgarve.jogoacoes.login.User;
import dev.leilaalgarve.jogoacoes.login.UserRepository;
import dev.leilaalgarve.jogoacoes.login.UserRole;
import dev.leilaalgarve.jogoacoes.login.UserRoleId;
import dev.leilaalgarve.jogoacoes.login.UserRoleRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Spec 05-014 -- there's no API to create an administrator (only an administrator can create
 * competitions), so a blackbox client (Swagger UI, or the Python suite in spec 05-015) has no
 * way to reach any admin-only flow without one already existing in the database. Seeds a known
 * administrator on startup, idempotently (safe across restarts), the same
 * user/role/user_role wiring {@code UserMother.administrator()} already uses in the Cucumber
 * suite. There's no password anywhere in this system -- login is always via magic link.
 *
 * {@code @Profile("blackbox")} keeps this from ever running outside the blackbox environment,
 * structurally (not just by leaving {@code captcha.verifier} at its default) -- the same
 * guarantee applied to {@link dev.leilaalgarve.jogoacoes.blackbox.BlackboxController}.
 */
@Component
@Profile("blackbox")
public class BlackboxDataSeeder implements ApplicationRunner {

    public static final String ADMIN_EMAIL = "admin@blackbox.local";
    private static final String ADMIN_NAME = "Blackbox administrator";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    public BlackboxDataSeeder(UserRepository userRepository, RoleRepository roleRepository,
                               UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByEmail(ADMIN_EMAIL).isPresent()) {
            return;
        }

        User admin = new User();
        admin.setName(ADMIN_NAME);
        admin.setEmail(ADMIN_EMAIL);
        admin.setRegistered(true);
        admin = userRepository.save(admin);

        Role administratorRole = roleRepository.findByName(RoleName.ADMINISTRATOR)
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + RoleName.ADMINISTRATOR));

        UserRole userRole = new UserRole();
        userRole.setId(new UserRoleId(admin.getId(), administratorRole.getId()));
        userRole.setUser(admin);
        userRole.setRole(administratorRole);
        userRole.setAssignedAt(LocalDateTime.now());
        userRoleRepository.save(userRole);
    }
}
