package dev.leilaalgarve.jogoacoes.bootstrap;

import dev.leilaalgarve.jogoacoes.user.RoleName;
import dev.leilaalgarve.jogoacoes.user.UserProvisioningService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Bootstraps the system's first administrator from the ADMIN_EMAIL environment variable, the
 * same pattern docker-compose.yml's own `db` service already uses (POSTGRES_USER/
 * POSTGRES_PASSWORD, effective only while the initial state -- an empty volume -- still holds).
 * Runs in every profile, not gated by any @Profile: before this, there was no way at all to
 * create an administrator in a staging/production deploy (only the now-removed, blackbox-only
 * BlackboxDataSeeder created one, and only for tests). There is still no API to create an
 * administrator (only an administrator can create competitions) -- this is the only way one
 * comes to exist, same reasoning as the class it replaces.
 *
 * ADMIN_EMAIL has no default here -- in staging/production, an unset/typo'd value should do
 * nothing, not create a permanent administrator nobody can reach (this never creates a second
 * one, so that mistake could never be corrected later). application-docker.yml sets its own
 * ADMIN_EMAIL default (SES's mailbox simulator address), so the dev/test environment still gets
 * an administrator automatically; a real env var there still wins, standard Spring Boot property
 * precedence.
 *
 * Idempotent by "does any administrator already exist", not by e-mail: once any administrator
 * exists, this never acts again, even if ADMIN_EMAIL changes or is unset on a later restart.
 */
@Component
public class AdministratorBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdministratorBootstrap.class);
    private static final String DEFAULT_NAME = "Administrator";

    private final UserProvisioningService userProvisioningService;
    private final Validator validator;
    private final String adminEmail;
    private final String adminName;

    public AdministratorBootstrap(UserProvisioningService userProvisioningService, Validator validator,
                                   @Value("${ADMIN_EMAIL:}") String adminEmail,
                                   @Value("${ADMIN_NAME:" + DEFAULT_NAME + "}") String adminName) {
        this.userProvisioningService = userProvisioningService;
        this.validator = validator;
        this.adminEmail = adminEmail;
        this.adminName = adminName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank()) {
            return; // Unset -- same as today's staging/production behavior.
        }
        if (userProvisioningService.existsAnyWithRole(RoleName.ADMINISTRATOR)) {
            return; // Already bootstrapped (or an administrator exists for any other reason).
        }
        if (!isValidEmail(adminEmail)) {
            log.error("ADMIN_EMAIL is set but not a valid e-mail address: '{}' -- skipping administrator bootstrap", adminEmail);
            return;
        }

        userProvisioningService.createUser(adminEmail, adminName, List.of(RoleName.ADMINISTRATOR));
        log.info("Bootstrapped the first administrator: {}", adminEmail);
    }

    private boolean isValidEmail(String value) {
        Set<ConstraintViolation<EmailHolder>> violations = validator.validate(new EmailHolder(value));
        return violations.isEmpty();
    }

    private record EmailHolder(@Email String email) {
    }
}
