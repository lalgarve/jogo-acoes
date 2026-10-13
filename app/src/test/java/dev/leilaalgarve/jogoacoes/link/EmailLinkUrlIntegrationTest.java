package dev.leilaalgarve.jogoacoes.link;

import dev.leilaalgarve.jogoacoes.api.model.DecideInviteEmailTimingRequest;
import dev.leilaalgarve.jogoacoes.api.model.EntryRequest;
import dev.leilaalgarve.jogoacoes.api.model.RequestLoginLinkRequest;
import dev.leilaalgarve.jogoacoes.captcha.AltchaCaptchaVerifier;
import dev.leilaalgarve.jogoacoes.common.testsupport.CompetitionFixtures;
import dev.leilaalgarve.jogoacoes.common.testsupport.TestEmails;
import dev.leilaalgarve.jogoacoes.common.testsupport.UserMother;
import dev.leilaalgarve.jogoacoes.competition.Competition;
import dev.leilaalgarve.jogoacoes.competition.CompetitionService;
import dev.leilaalgarve.jogoacoes.competition.CompetitionStatus;
import dev.leilaalgarve.jogoacoes.competition.CompetitionType;
import dev.leilaalgarve.jogoacoes.competition.EntryRequestService;
import dev.leilaalgarve.jogoacoes.competition.Participation;
import dev.leilaalgarve.jogoacoes.competition.ParticipationRepository;
import dev.leilaalgarve.jogoacoes.competition.ParticipationStatus;
import dev.leilaalgarve.jogoacoes.competition.PlayerManagementService;
import dev.leilaalgarve.jogoacoes.competition.RequestType;
import dev.leilaalgarve.jogoacoes.email.SentEmail;
import dev.leilaalgarve.jogoacoes.email.SentEmailRepository;
import dev.leilaalgarve.jogoacoes.loginsession.LoginController;
import dev.leilaalgarve.jogoacoes.user.User;
import org.altcha.altcha.v2.Altcha;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-041: every e-mail that carries an access link carries an absolute URL,
 * {@code <links.base-url>/login-links/<token>} -- checked on the four call sites that send one,
 * through the stub sender's sent_email row, against the real Postgres of the docker profile
 * (whose base is http://localhost:8080/api).
 */
@SpringBootTest
class EmailLinkUrlIntegrationTest {

    private static final String BASE = "http://localhost:8080/api/login-links/";

    @Autowired
    private LoginController loginController;

    @Autowired
    private CompetitionService competitionService;

    @Autowired
    private EntryRequestService entryRequestService;

    @Autowired
    private PlayerManagementService playerManagementService;

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    private SentEmailRepository sentEmailRepository;

    @Autowired
    private LinkRecordRepository linkRecordRepository;

    @Autowired
    private AltchaCaptchaVerifier captchaVerifier;

    @Autowired
    private CompetitionFixtures competitionFixtures;

    @Autowired
    private UserMother userMother;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginRequestSendsAnAbsoluteLink() {
        User player = userMother.registeredPlayer();

        loginController.requestLoginLink(new RequestLoginLinkRequest().email(player.getEmail()));

        assertSentLinkIsAbsolute(player.getEmail());
    }

    @Test
    void sendingPrivateCompetitionInvitesSendsAbsoluteLinks() {
        Competition competition = competitionFixtures.custom(CompetitionType.PRIVATE, CompetitionStatus.AWAITING_INVITES);
        String email = TestEmails.unique("invite-now");
        participationRepository.save(participation(competition, email, ParticipationStatus.EMAIL_NOT_SENT));
        authenticateAs(competition.getCreator());

        competitionService.decideInviteEmailTiming(competition.getId(), DecideInviteEmailTimingRequest.TimingEnum.NOW);

        assertSentLinkIsAbsolute(email);
    }

    @Test
    void publicCompetitionEntryRequestSendsAnAbsoluteLink() throws Exception {
        Competition competition = competitionFixtures.publicCompetition();
        String email = TestEmails.unique("entry-request");

        entryRequestService.requestEntry(competition.getId(),
                new EntryRequest().email(email).captchaToken(validCaptchaToken()));

        assertSentLinkIsAbsolute(email);
    }

    @Test
    void resendingAnInviteSendsAnAbsoluteLink() {
        Competition competition = competitionFixtures.custom(CompetitionType.PRIVATE, CompetitionStatus.OPEN);
        String email = TestEmails.unique("invite-resend");
        Participation participation = participationRepository.save(
                participation(competition, email, ParticipationStatus.EMAIL_SENT));
        authenticateAs(competition.getCreator());

        playerManagementService.resendInviteEmail(competition.getId(), participation.getId());

        assertSentLinkIsAbsolute(email);
    }

    private void assertSentLinkIsAbsolute(String email) {
        List<SentEmail> sent = sentEmailRepository.findAll().stream()
                .filter(e -> e.getEmail().equals(email))
                .toList();
        assertThat(sent).hasSize(1);
        String link = sent.get(0).getLink();
        assertThat(link).startsWith(BASE);
        String token = link.substring(BASE.length());
        assertThat(linkRecordRepository.findByToken(token)).isPresent();
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

    private String validCaptchaToken() throws Exception {
        Altcha.Challenge challenge = captchaVerifier.createChallenge();
        Altcha.Solution solution = Altcha.solveChallenge(challenge, Altcha.kdf(challenge.parameters().algorithm()));
        return captchaVerifier.encodeToken(challenge, solution);
    }
}
