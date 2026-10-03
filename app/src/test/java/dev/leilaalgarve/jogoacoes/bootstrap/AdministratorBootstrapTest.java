package dev.leilaalgarve.jogoacoes.bootstrap;

import dev.leilaalgarve.jogoacoes.user.RoleName;
import dev.leilaalgarve.jogoacoes.user.UserProvisioningService;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdministratorBootstrapTest {

    @Mock
    private UserProvisioningService userProvisioningService;

    // Real Jakarta Bean Validation (Hibernate Validator), not mocked -- the behavior under test
    // is "does @Email actually reject this string", a mock would just assert its own stub.
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void blankAdminEmailDoesNotCreateAnyone() {
        AdministratorBootstrap bootstrap = bootstrapWith("", "Administrator");

        bootstrap.run(null);

        verify(userProvisioningService, never()).createUser(any(), any(), any());
    }

    @Test
    void anExistingAdministratorPreventsCreatingAnotherOneEvenWithAdminEmailSet() {
        when(userProvisioningService.existsAnyWithRole(RoleName.ADMINISTRATOR)).thenReturn(true);
        AdministratorBootstrap bootstrap = bootstrapWith("admin@example.com", "Administrator");

        bootstrap.run(null);

        verify(userProvisioningService, never()).createUser(any(), any(), any());
    }

    @Test
    void validAdminEmailWithNoAdministratorYetCreatesOneWithTheDefaultName() {
        when(userProvisioningService.existsAnyWithRole(RoleName.ADMINISTRATOR)).thenReturn(false);
        AdministratorBootstrap bootstrap = bootstrapWith("admin@example.com", "Administrator");

        bootstrap.run(null);

        verify(userProvisioningService).createUser("admin@example.com", "Administrator", List.of(RoleName.ADMINISTRATOR));
    }

    @Test
    void adminNameSetUsesThatNameInsteadOfTheDefault() {
        when(userProvisioningService.existsAnyWithRole(RoleName.ADMINISTRATOR)).thenReturn(false);
        AdministratorBootstrap bootstrap = bootstrapWith("admin@example.com", "Leila");

        bootstrap.run(null);

        verify(userProvisioningService).createUser("admin@example.com", "Leila", List.of(RoleName.ADMINISTRATOR));
    }

    @Test
    void malformedAdminEmailDoesNotCreateAnyone() {
        when(userProvisioningService.existsAnyWithRole(RoleName.ADMINISTRATOR)).thenReturn(false);
        AdministratorBootstrap bootstrap = bootstrapWith("not an email", "Administrator");

        bootstrap.run(null);

        verify(userProvisioningService, never()).createUser(any(), any(), any());
    }

    private AdministratorBootstrap bootstrapWith(String adminEmail, String adminName) {
        return new AdministratorBootstrap(userProvisioningService, validator, adminEmail, adminName);
    }

    private static <T> T any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
