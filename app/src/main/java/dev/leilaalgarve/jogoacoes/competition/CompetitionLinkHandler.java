package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.link.LinkHandler;
import dev.leilaalgarve.jogoacoes.link.LinkOutcome;
import dev.leilaalgarve.jogoacoes.link.LoginLinkInvalidException;
import dev.leilaalgarve.jogoacoes.link.dto.LinkPayload;
import dev.leilaalgarve.jogoacoes.log.AuditLogService;
import dev.leilaalgarve.jogoacoes.log.LogType;
import dev.leilaalgarve.jogoacoes.login.Role;
import dev.leilaalgarve.jogoacoes.login.RoleName;
import dev.leilaalgarve.jogoacoes.login.RoleRepository;
import dev.leilaalgarve.jogoacoes.login.User;
import dev.leilaalgarve.jogoacoes.login.UserRepository;
import dev.leilaalgarve.jogoacoes.login.UserRole;
import dev.leilaalgarve.jogoacoes.login.UserRoleId;
import dev.leilaalgarve.jogoacoes.login.UserRoleRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Competition invite/entry-request link — covers both cases (an admin's invite and a public
 * entry request produce the exact same link shape and behave identically on consumption, see
 * plan.md). {@code payload.extra().get("participationId")} is the only thing this module puts
 * in the generic `extra` bag; {@code EntryRequestService.confirmEntry} is out of scope, it never
 * uses a link/token.
 */
@Component
public class CompetitionLinkHandler implements LinkHandler {

    public static final String KEY = "competition-entry";
    public static final String PARTICIPATION_ID_EXTRA_KEY = "participationId";

    private final ParticipationRepository participationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditLogService auditLogService;

    public CompetitionLinkHandler(ParticipationRepository participationRepository, UserRepository userRepository,
                                   RoleRepository roleRepository, UserRoleRepository userRoleRepository,
                                   AuditLogService auditLogService) {
        this.participationRepository = participationRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public LinkOutcome consume(LinkPayload payload) {
        Participation participation = participationFor(payload);
        rejectIfClosedAndUnfinished(participation);
        if (payload.userId() == null) {
            return LinkOutcome.pending();
        }
        return redirectFor(participation);
    }

    @Override
    public LinkOutcome alreadyAuthenticated(Long authenticatedUserId, LinkPayload payload) {
        return redirectFor(participationFor(payload));
    }

    @Override
    @Transactional
    public LinkOutcome complete(LinkPayload payload, Map<String, String> extra) {
        Participation participation = participationFor(payload);

        User user = new User();
        user.setName(extra.get("name"));
        user.setEmail(payload.email());
        user.setRegistered(true);
        user = userRepository.save(user);
        assignRole(user, RoleName.PLAYER);

        participation.setUser(user);
        participation.setStatus(ParticipationStatus.IN_COMPETITION);
        participation.setJoinedAt(LocalDate.now());
        participation = participationRepository.save(participation);
        auditLogService.record(LogType.PARTICIPATION_STATUS_CHANGED, participation.getId(), user,
                "Participation status changed to IN_COMPETITION (registration completed)");

        return redirectFor(participation, user.getId());
    }

    private Participation participationFor(LinkPayload payload) {
        String rawParticipationId = payload.extra().get(PARTICIPATION_ID_EXTRA_KEY);
        if (rawParticipationId == null) {
            throw new IllegalArgumentException("Missing '" + PARTICIPATION_ID_EXTRA_KEY + "' in link extra data");
        }
        Long participationId;
        try {
            participationId = Long.valueOf(rawParticipationId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid '" + PARTICIPATION_ID_EXTRA_KEY + "' in link extra data: " + rawParticipationId);
        }
        return participationRepository.findById(participationId)
                .orElseThrow(() -> new LoginLinkInvalidException("Link invalid or expired"));
    }

    private void rejectIfClosedAndUnfinished(Participation participation) {
        boolean finished = participation.getStatus() == ParticipationStatus.IN_COMPETITION;
        if (participation.getCompetition().getStatus() == CompetitionStatus.CLOSED && !finished) {
            throw new LoginLinkInvalidException("Link invalid or expired");
        }
    }

    private LinkOutcome redirectFor(Participation participation) {
        Long userId = participation.getUser() != null ? participation.getUser().getId() : null;
        return redirectFor(participation, userId);
    }

    private LinkOutcome redirectFor(Participation participation, Long userId) {
        Map<String, String> redirectData = new HashMap<>();
        redirectData.put("redirectTo", "/competitions/" + participation.getCompetition().getId());
        return LinkOutcome.authenticated(userId, redirectData);
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
