package dev.leilaalgarve.jogoacoes.login;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProvisioningServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    private UserProvisioningService service;

    @Test
    void createUserWithOneRoleSavesTheUserAndOneUserRole() {
        service = new UserProvisioningService(userRepository, roleRepository, userRoleRepository);
        User savedUser = userWithId(1L);
        when(userRepository.save(any())).thenReturn(savedUser);
        when(roleRepository.findByName(RoleName.ADMINISTRATOR)).thenReturn(Optional.of(roleWithId(RoleName.ADMINISTRATOR, 10L)));

        User result = service.createUser("admin@example.com", "Administrator", List.of(RoleName.ADMINISTRATOR));

        assertThat(result).isSameAs(savedUser);
        verify(userRoleRepository).save(any());
    }

    @Test
    void createUserWithTwoRolesSavesTwoUserRoles() {
        service = new UserProvisioningService(userRepository, roleRepository, userRoleRepository);
        when(userRepository.save(any())).thenReturn(userWithId(2L));
        when(roleRepository.findByName(RoleName.ADMINISTRATOR)).thenReturn(Optional.of(roleWithId(RoleName.ADMINISTRATOR, 10L)));
        when(roleRepository.findByName(RoleName.PLAYER)).thenReturn(Optional.of(roleWithId(RoleName.PLAYER, 20L)));

        service.createUser("both@example.com", "Both Roles", List.of(RoleName.ADMINISTRATOR, RoleName.PLAYER));

        verify(userRoleRepository, times(2)).save(any());
    }

    @Test
    void existsAnyWithRoleDelegatesToTheRepository() {
        service = new UserProvisioningService(userRepository, roleRepository, userRoleRepository);
        when(userRoleRepository.existsByRole_Name(RoleName.ADMINISTRATOR)).thenReturn(true);

        assertThat(service.existsAnyWithRole(RoleName.ADMINISTRATOR)).isTrue();
    }

    @Test
    void createUserWithAnUnseededRoleThrows() {
        service = new UserProvisioningService(userRepository, roleRepository, userRoleRepository);
        when(userRepository.save(any())).thenReturn(userWithId(3L));
        when(roleRepository.findByName("GHOST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createUser("ghost@example.com", "Ghost", List.of("GHOST")))
                .isInstanceOf(IllegalStateException.class);
    }

    private User userWithId(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private Role roleWithId(String name, Long id) {
        Role role = new Role();
        role.setId(id);
        role.setName(name);
        return role;
    }
}
