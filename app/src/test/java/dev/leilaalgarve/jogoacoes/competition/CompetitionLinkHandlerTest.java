package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.link.LinkOutcome;
import dev.leilaalgarve.jogoacoes.link.exception.LoginLinkInvalidException;
import dev.leilaalgarve.jogoacoes.link.dto.LinkPayload;
import dev.leilaalgarve.jogoacoes.log.AuditLogService;
import dev.leilaalgarve.jogoacoes.login.Role;
import dev.leilaalgarve.jogoacoes.login.RoleName;
import dev.leilaalgarve.jogoacoes.login.RoleRepository;
import dev.leilaalgarve.jogoacoes.login.User;
import dev.leilaalgarve.jogoacoes.login.UserRepository;
import dev.leilaalgarve.jogoacoes.login.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** The dedicated verification test spec 05-003 requires for every LinkHandler implementation. */
@ExtendWith(MockitoExtension.class)
class CompetitionLinkHandlerTest {

    @Mock
    private ParticipationRepository participationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private AuditLogService auditLogService;

    private CompetitionLinkHandler handler() {
        return new CompetitionLinkHandler(participationRepository, userRepository, roleRepository, userRoleRepository, auditLogService);
    }

    @Test
    void keyIsCompetitionEntry() {
        assertThat(handler().key()).isEqualTo(CompetitionLinkHandler.KEY);
    }

    @Test
    void consumeRedirectsAnAlreadyRegisteredPlayerToTheCompetitionPage() {
        Competition competition = competitionWithStatus(CompetitionStatus.OPEN, 10L);
        User user = userWithId(5L);
        Participation participation = participationFor(competition, user, ParticipationStatus.EMAIL_SENT);
        when(participationRepository.findById(1L)).thenReturn(Optional.of(participation));

        LinkOutcome outcome = handler().consume(payload(user.getId(), 1L));

        assertThat(outcome.isPending()).isFalse();
        assertThat(outcome.userId()).isEqualTo(5L);
        assertThat(outcome.redirectData()).containsEntry("redirectTo", "/competitions/10");
    }

    @Test
    void consumeReturnsPendingWhenNoAccountExistsYet() {
        Competition competition = competitionWithStatus(CompetitionStatus.OPEN, 10L);
        Participation participation = participationFor(competition, null, ParticipationStatus.EMAIL_SENT);
        when(participationRepository.findById(1L)).thenReturn(Optional.of(participation));

        LinkOutcome outcome = handler().consume(payload(null, 1L));

        assertThat(outcome.isPending()).isTrue();
    }

    @Test
    void consumeRejectsAnUnfinishedParticipationOnAClosedCompetition() {
        Competition competition = competitionWithStatus(CompetitionStatus.CLOSED, 10L);
        Participation participation = participationFor(competition, null, ParticipationStatus.EMAIL_SENT);
        when(participationRepository.findById(1L)).thenReturn(Optional.of(participation));

        assertThatThrownBy(() -> handler().consume(payload(null, 1L)))
                .isInstanceOf(LoginLinkInvalidException.class);
    }

    @Test
    void consumeAllowsAFinishedParticipationOnAClosedCompetition() {
        Competition competition = competitionWithStatus(CompetitionStatus.CLOSED, 10L);
        User user = userWithId(5L);
        Participation participation = participationFor(competition, user, ParticipationStatus.IN_COMPETITION);
        when(participationRepository.findById(1L)).thenReturn(Optional.of(participation));

        LinkOutcome outcome = handler().consume(payload(user.getId(), 1L));

        assertThat(outcome.isPending()).isFalse();
        assertThat(outcome.redirectData()).containsEntry("redirectTo", "/competitions/10");
    }

    @Test
    void completeRegistersTheNewPlayerAndConfirmsEntry() {
        Competition competition = competitionWithStatus(CompetitionStatus.OPEN, 10L);
        Participation participation = participationFor(competition, null, ParticipationStatus.EMAIL_SENT);
        when(participationRepository.findById(1L)).thenReturn(Optional.of(participation));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });
        Role playerRole = new Role();
        playerRole.setId(2L);
        playerRole.setName(RoleName.PLAYER);
        when(roleRepository.findByName(RoleName.PLAYER)).thenReturn(Optional.of(playerRole));
        when(participationRepository.save(any(Participation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LinkOutcome outcome = handler().complete(payload(null, 1L), Map.of("name", "New Player"));

        assertThat(outcome.userId()).isEqualTo(7L);
        assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.IN_COMPETITION);
        assertThat(participation.getUser().getId()).isEqualTo(7L);
        assertThat(outcome.redirectData()).containsEntry("redirectTo", "/competitions/10");
    }

    @Test
    void consumeRejectsAPayloadMissingTheParticipationId() {
        assertThatThrownBy(() -> handler().consume(new LinkPayload(null, "someone@example.com", Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consumeRejectsAPayloadWithANonNumericParticipationId() {
        Map<String, String> extra = Map.of(CompetitionLinkHandler.PARTICIPATION_ID_EXTRA_KEY, "not-a-number");

        assertThatThrownBy(() -> handler().consume(new LinkPayload(null, "someone@example.com", extra)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void consumeRejectsAParticipationThatNoLongerExists() {
        when(participationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler().consume(payload(null, 999L)))
                .isInstanceOf(LoginLinkInvalidException.class);
    }

    private LinkPayload payload(Long userId, long participationId) {
        return new LinkPayload(userId, "player@example.com",
                Map.of(CompetitionLinkHandler.PARTICIPATION_ID_EXTRA_KEY, String.valueOf(participationId)));
    }

    private Competition competitionWithStatus(CompetitionStatus status, Long id) {
        Competition competition = new Competition();
        competition.setId(id);
        competition.setStatus(status);
        return competition;
    }

    private Participation participationFor(Competition competition, User user, ParticipationStatus status) {
        Participation participation = new Participation();
        participation.setId(1L);
        participation.setCompetition(competition);
        participation.setUser(user);
        participation.setEmail(user != null ? user.getEmail() : "player@example.com");
        participation.setStatus(status);
        participation.setRequestType(RequestType.REQUEST);
        return participation;
    }

    private User userWithId(Long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("player" + id + "@example.com");
        user.setName("Player " + id);
        user.setRegistered(true);
        return user;
    }
}
