package io.deployo.jogoacoes.service;

import io.deployo.jogoacoes.api.model.CompetitionCreateRequest;
import io.deployo.jogoacoes.api.model.DecideInviteEmailTimingRequest;
import io.deployo.jogoacoes.domain.Competition;
import io.deployo.jogoacoes.domain.Log;
import io.deployo.jogoacoes.domain.LogType;
import io.deployo.jogoacoes.domain.LoginLink;
import io.deployo.jogoacoes.domain.Participation;
import io.deployo.jogoacoes.domain.RequestType;
import io.deployo.jogoacoes.domain.User;
import io.deployo.jogoacoes.repository.LogRepository;
import io.deployo.jogoacoes.repository.ParticipationRepository;
import io.deployo.jogoacoes.testsupport.CompetitionFixtures;
import io.deployo.jogoacoes.testsupport.CompetitionMother;
import io.deployo.jogoacoes.testsupport.LoginLinkFixtures;
import io.deployo.jogoacoes.testsupport.UserMother;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the four business services directly (bypassing HTTP/Cucumber) to prove each
 * calls AuditLogService at the points docs/diagrams/der.md calls out as auditable --
 * competition creation, participation status changes and login link issuance -- rather than
 * just testing AuditLogService in isolation (see AuditLogServiceTest).
 *
 * @Transactional rolls each test back -- tests/application.yml points at a named in-memory
 * H2 instance shared by every test class in the same Surefire run (not a fresh one per
 * class), so without a rollback these services' real writes (audit logs, stub-sent e-mails)
 * would leak into unrelated tests asserting exact row counts (e.g. StubEmailSenderTest).
 */
@SpringBootTest
@Transactional
class AuditLoggingIntegrationTest {

    @Autowired
    private CompetitionService competitionService;

    @Autowired
    private EntryRequestService entryRequestService;

    @Autowired
    private LoginService loginService;

    @Autowired
    private PlayerManagementService playerManagementService;

    @Autowired
    private LogRepository logRepository;

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    private UserMother userMother;

    @Autowired
    private CompetitionFixtures competitionFixtures;

    @Autowired
    private LoginLinkFixtures loginLinkFixtures;

    @BeforeEach
    void bindMockHttpRequest() {
        // LoginService.markUsedAndEstablishSession reads the User-Agent header and saves the
        // security context onto the request/response -- both request-scoped beans, so a call
        // needs a thread-bound request the same way a real HTTP call to the app would provide it.
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest(), new MockHttpServletResponse()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void creatingAPrivateCompetitionAuditsTheCompetitionAndEachInvite() {
        User admin = userMother.administrator();
        authenticateAs(admin);

        CompetitionCreateRequest request = CompetitionMother.validPrivateCompetition();
        Competition competition = competitionService.create(request);

        List<Log> logs = logRepository.findAll();
        assertThat(logs).anySatisfy(log -> {
            assertThat(log.getLogType()).isEqualTo(LogType.COMPETITION_CREATED);
            assertThat(log.getRelatedObjectId()).isEqualTo(competition.getId());
            assertThat(log.getUser().getId()).isEqualTo(admin.getId());
        });

        List<Participation> invitees = participationRepository.findByCompetition_Id(competition.getId());
        assertThat(invitees).hasSize(2);
        for (Participation invitee : invitees) {
            assertThat(logs).anySatisfy(log -> {
                assertThat(log.getLogType()).isEqualTo(LogType.PARTICIPATION_STATUS_CHANGED);
                assertThat(log.getRelatedObjectId()).isEqualTo(invitee.getId());
                assertThat(log.getMessage()).contains("EMAIL_NOT_SENT");
            });
        }
    }

    @Test
    void sendingInviteEmailsAuditsTheLoginLinkAndTheStatusChange() {
        User admin = userMother.administrator();
        authenticateAs(admin);
        Competition competition = competitionService.create(CompetitionMother.validPrivateCompetition());

        competitionService.decideInviteEmailTiming(competition.getId(), DecideInviteEmailTimingRequest.TimingEnum.NOW);

        List<Log> logs = logRepository.findAll();
        assertThat(logs).filteredOn(log -> log.getLogType() == LogType.LOGIN_LINK_ISSUED).hasSize(2);
        assertThat(logs).filteredOn(log -> log.getLogType() == LogType.PARTICIPATION_STATUS_CHANGED
                && log.getMessage().contains("EMAIL_SENT")).hasSize(2);
    }

    @Test
    void confirmingEntryAsALoggedInPlayerAuditsTheStatusChange() {
        Competition publicCompetition = competitionFixtures.publicCompetition();
        User player = userMother.registeredPlayer();
        authenticateAs(player);

        Participation participation = entryRequestService.confirmEntry(publicCompetition.getId());

        List<Log> logs = logRepository.findAll();
        assertThat(logs).anySatisfy(log -> {
            assertThat(log.getLogType()).isEqualTo(LogType.PARTICIPATION_STATUS_CHANGED);
            assertThat(log.getRelatedObjectId()).isEqualTo(participation.getId());
            assertThat(log.getUser().getId()).isEqualTo(player.getId());
            assertThat(log.getMessage()).contains("IN_COMPETITION");
        });
    }

    @Test
    void requestingALoginLinkAuditsTheIssuance() {
        User user = userMother.registeredPlayer();

        loginService.requestLoginLink(user.getEmail());

        List<Log> logs = logRepository.findAll();
        assertThat(logs).anySatisfy(log -> {
            assertThat(log.getLogType()).isEqualTo(LogType.LOGIN_LINK_ISSUED);
            assertThat(log.getUser().getId()).isEqualTo(user.getId());
        });
    }

    @Test
    void completingRegistrationAuditsTheStatusChange() {
        Competition publicCompetition = competitionFixtures.publicCompetition();
        String email = "newplayer-" + UUID.randomUUID() + "@example.com";
        LoginLink link = loginLinkFixtures.pendingParticipationLink(publicCompetition, email, RequestType.REQUEST);
        Long participationId = link.getParticipation().getId();

        loginService.completeRegistration(link.getToken(), "New Player");

        List<Log> logs = logRepository.findAll();
        assertThat(logs).anySatisfy(log -> {
            assertThat(log.getLogType()).isEqualTo(LogType.PARTICIPATION_STATUS_CHANGED);
            assertThat(log.getRelatedObjectId()).isEqualTo(participationId);
            assertThat(log.getMessage()).contains("IN_COMPETITION");
        });
    }

    @Test
    void invitingAndThenRemovingAPlayerAuditsBothTheIssuanceAndTheRemoval() {
        Competition competition = competitionFixtures.privateCompetition();
        User admin = userMother.administrator();
        authenticateAs(admin);
        String email = "invitee-" + UUID.randomUUID() + "@example.com";

        playerManagementService.invitePlayers(competition.getId(), List.of(email));
        Long participationId = participationRepository.findByCompetition_Id(competition.getId()).get(0).getId();

        assertThat(logRepository.findAll())
                .anySatisfy(log -> assertThat(log.getLogType()).isEqualTo(LogType.LOGIN_LINK_ISSUED))
                .anySatisfy(log -> {
                    assertThat(log.getLogType()).isEqualTo(LogType.PARTICIPATION_STATUS_CHANGED);
                    assertThat(log.getRelatedObjectId()).isEqualTo(participationId);
                    assertThat(log.getMessage()).contains("EMAIL_SENT");
                });

        playerManagementService.removePlayer(competition.getId(), participationId);

        // The removal log outlives the participation row it refers to -- LOG.related_object_id
        // is deliberately not a real FK (der.md), precisely so an audit trail survives deletion.
        assertThat(participationRepository.findById(participationId)).isEmpty();
        assertThat(logRepository.findAll()).anySatisfy(log -> {
            assertThat(log.getLogType()).isEqualTo(LogType.PARTICIPATION_STATUS_CHANGED);
            assertThat(log.getRelatedObjectId()).isEqualTo(participationId);
            assertThat(log.getMessage()).contains("removed");
        });
    }

    private void authenticateAs(User user) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of()));
        SecurityContextHolder.setContext(context);
    }
}
