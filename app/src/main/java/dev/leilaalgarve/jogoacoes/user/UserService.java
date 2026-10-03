package dev.leilaalgarve.jogoacoes.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Read side of the `user` module for every other module (spec 05-029): modules talk to each
 * other only through services, so {@link UserRepository}/{@link UserRoleRepository} stay
 * internal to `user`. Creating users is {@link UserProvisioningService}'s job, not this one's.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    public UserService(UserRepository userRepository, UserRoleRepository userRoleRepository) {
        this.userRepository = userRepository;
        this.userRoleRepository = userRoleRepository;
    }

    /** Any user with this e-mail, whether or not they finished registration. */
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /** Only a user who already finished registration ({@link User#isRegistered()}). */
    public Optional<User> findRegisteredByEmail(String email) {
        return userRepository.findByEmail(email).filter(User::isRegistered);
    }

    /**
     * For ids that come from inside the system (a link, a queued e-mail), never from user input
     * -- a missing one is a bug, hence {@link IllegalStateException}.
     */
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("User not found: " + id));
    }

    /** Role names ({@link RoleName}) held by this user. */
    public List<String> roleNamesOf(Long userId) {
        return userRoleRepository.findByUser_Id(userId).stream()
                .map(userRole -> userRole.getRole().getName())
                .toList();
    }

    public boolean hasRole(Long userId, String roleName) {
        return roleNamesOf(userId).contains(roleName);
    }
}
