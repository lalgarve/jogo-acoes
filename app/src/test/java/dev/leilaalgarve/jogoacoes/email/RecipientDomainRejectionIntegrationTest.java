package dev.leilaalgarve.jogoacoes.email;

import dev.leilaalgarve.jogoacoes.api.model.DecideInviteEmailTimingRequest;
import dev.leilaalgarve.jogoacoes.common.testsupport.CompetitionFixtures;
import dev.leilaalgarve.jogoacoes.common.testsupport.TestEmails;
import dev.leilaalgarve.jogoacoes.common.testsupport.UserMother;
import dev.leilaalgarve.jogoacoes.competition.Competition;
import dev.leilaalgarve.jogoacoes.competition.CompetitionRepository;
import dev.leilaalgarve.jogoacoes.competition.CompetitionService;
import dev.leilaalgarve.jogoacoes.competition.CompetitionStatus;
import dev.leilaalgarve.jogoacoes.competition.CompetitionType;
import dev.leilaalgarve.jogoacoes.competition.Participation;
import dev.leilaalgarve.jogoacoes.competition.ParticipationRepository;
import dev.leilaalgarve.jogoacoes.competition.ParticipationStatus;
import dev.leilaalgarve.jogoacoes.competition.PlayerManagementService;
import dev.leilaalgarve.jogoacoes.competition.RequestType;
import dev.leilaalgarve.jogoacoes.email.exception.RecipientDomainRejectedException;
import dev.leilaalgarve.jogoacoes.user.User;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 05-047: app against the real email-service, which checks the recipient's domain in the
 * test DNS (docker/dns/test.zone, where missing.test has no record). Needs the whole Compose
 * infrastructure up, dns included; missing infrastructure is an error, never a skip.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "email-service.base-url=http://localhost:8082/api",
        "email.sender=email-service"})
// The only class with this configuration: closing its context frees its database connections
// for the rest of the suite (each cached context keeps a pool of its own).
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RecipientDomainRejectionIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserMother userMother;

    @Autowired
    private CompetitionFixtures competitionFixtures;

    @Autowired
    private CompetitionRepository competitionRepository;

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    private CompetitionService competitionService;

    @Autowired
    private PlayerManagementService playerManagementService;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aLoginLinkToADomainThatTakesNoEmailIsRejected() {
        User player = userMother.registeredPlayer("Ana", rejected());

        RestAssured.given()
                .port(port)
                .basePath("/api")
                .contentType("application/json")
                .body(Map.of("email", player.getEmail()))
                .post("/login-requests")
                .then()
                .statusCode(422)
                .body("message", org.hamcrest.Matchers.equalTo("The e-mail address domain does not accept e-mail"));
    }

    @Test
    void sendingInvitesNowSkipsOnlyTheRejectedAddress() {
        Competition competition = competitionFixtures.custom(CompetitionType.PRIVATE, CompetitionStatus.AWAITING_INVITES);
        Participation bad = participationRepository.save(participation(competition, rejected(), ParticipationStatus.EMAIL_NOT_SENT));
        Participation good = participationRepository.save(
                participation(competition, TestEmails.unique("domain-now"), ParticipationStatus.EMAIL_NOT_SENT));
        authenticateAs(competition.getCreator());

        competitionService.decideInviteEmailTiming(competition.getId(), DecideInviteEmailTimingRequest.TimingEnum.NOW);

        assertThat(statusOf(bad)).isEqualTo(ParticipationStatus.EMAIL_NOT_SENT);
        assertThat(statusOf(good)).isEqualTo(ParticipationStatus.EMAIL_SENT);
        assertThat(competitionRepository.findById(competition.getId()).orElseThrow().getStatus())
                .isEqualTo(CompetitionStatus.OPEN);
    }

    @Test
    void invitingAGroupSkipsOnlyTheRejectedAddress() {
        Competition competition = competitionFixtures.custom(CompetitionType.PRIVATE, CompetitionStatus.OPEN);
        String bad = rejected();
        String good = TestEmails.unique("domain-invite");
        authenticateAs(competition.getCreator());

        playerManagementService.invitePlayers(competition.getId(), List.of(bad, good));

        assertThat(statusOf(competition, bad)).isEqualTo(ParticipationStatus.EMAIL_NOT_SENT);
        assertThat(statusOf(competition, good)).isEqualTo(ParticipationStatus.EMAIL_SENT);
    }

    @Test
    void resendingToAGroupSkipsOnlyTheRejectedAddress() {
        Competition competition = competitionFixtures.custom(CompetitionType.PRIVATE, CompetitionStatus.OPEN);
        Participation bad = participationRepository.save(participation(competition, rejected(), ParticipationStatus.EMAIL_NOT_SENT));
        Participation good = participationRepository.save(
                participation(competition, TestEmails.unique("domain-resend"), ParticipationStatus.EMAIL_NOT_SENT));
        authenticateAs(competition.getCreator());

        playerManagementService.resendInviteEmails(competition.getId(), List.of(bad.getId(), good.getId()));

        assertThat(statusOf(bad)).isEqualTo(ParticipationStatus.EMAIL_NOT_SENT);
        assertThat(statusOf(good)).isEqualTo(ParticipationStatus.EMAIL_SENT);
    }

    @Test
    void resendingToOneRejectedAddressReachesTheCaller() {
        Competition competition = competitionFixtures.custom(CompetitionType.PRIVATE, CompetitionStatus.OPEN);
        Participation bad = participationRepository.save(participation(competition, rejected(), ParticipationStatus.EMAIL_NOT_SENT));
        authenticateAs(competition.getCreator());

        assertThatThrownBy(() -> playerManagementService.resendInviteEmail(competition.getId(), bad.getId()))
                .isInstanceOf(RecipientDomainRejectedException.class);
        assertThat(statusOf(bad)).isEqualTo(ParticipationStatus.EMAIL_NOT_SENT);
    }

    private static String rejected() {
        return "jogador-" + UUID.randomUUID() + "@missing.test";
    }

    private ParticipationStatus statusOf(Participation participation) {
        return participationRepository.findById(participation.getId()).orElseThrow().getStatus();
    }

    private ParticipationStatus statusOf(Competition competition, String email) {
        return participationRepository.findByCompetition_Id(competition.getId()).stream()
                .filter(p -> p.getEmail().equals(email))
                .findFirst().orElseThrow()
                .getStatus();
    }

    private static Participation participation(Competition competition, String email, ParticipationStatus status) {
        Participation participation = new Participation();
        participation.setCompetition(competition);
        participation.setEmail(email);
        participation.setRequestType(RequestType.INVITE);
        participation.setStatus(status);
        if (status == ParticipationStatus.EMAIL_SENT) {
            participation.setFirstEmailSentDate(LocalDate.now());
        }
        return participation;
    }

    private static void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user.getEmail(), null, List.of()));
    }
}
