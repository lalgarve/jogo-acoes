package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.api.model.EntryRequest;
import dev.leilaalgarve.jogoacoes.captcha.AltchaCaptchaVerifier;
import dev.leilaalgarve.jogoacoes.common.testsupport.CompetitionFixtures;
import dev.leilaalgarve.jogoacoes.common.testsupport.UserMother;
import dev.leilaalgarve.jogoacoes.competition.exception.EntryRequestValidationException;
import dev.leilaalgarve.jogoacoes.email.SentEmailRepository;
import dev.leilaalgarve.jogoacoes.user.User;
import org.altcha.altcha.v2.Altcha;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static dev.leilaalgarve.jogoacoes.common.testsupport.TestEmails.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 05-036: an entry request with no e-mail, against the real Postgres of the docker profile.
 * The e-mail is checked before the captcha, so the token never reaches the verifier. With the
 * test context's stub sender, "no e-mail sent" means no new sent_email row.
 *
 * <p>Spec 05-057: one participation per e-mail and competition, whatever the case of the e-mail
 * and however many requests arrive at once.
 */
@SpringBootTest
class EntryRequestServiceIntegrationTest {

    @Autowired
    private EntryRequestService entryRequestService;

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    private SentEmailRepository sentEmailRepository;

    @Autowired
    private CompetitionFixtures competitionFixtures;

    @Autowired
    private UserMother userMother;

    @Autowired
    private AltchaCaptchaVerifier captchaVerifier;

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"   "})
    void requestingEntryWithoutAnEmailIsRejectedAndCreatesNothing(String email) {
        Competition competition = competitionFixtures.publicCompetition();
        long sentBefore = sentEmailRepository.count();

        assertThatThrownBy(() -> entryRequestService.requestEntry(competition.getId(),
                new EntryRequest().email(email).captchaToken("not-checked")))
                .isInstanceOf(EntryRequestValidationException.class);

        assertThat(participationRepository.findByCompetition_Id(competition.getId())).isEmpty();
        assertThat(sentEmailRepository.count()).isEqualTo(sentBefore);
    }

    @Test
    void simultaneousEntryRequestsForTheSameEmailCreateOneParticipation() throws Exception {
        Competition competition = competitionFixtures.publicCompetition();
        String email = unique("simultaneous");
        List<EntryRequest> requests = List.of(
                new EntryRequest().email(email).captchaToken(validCaptchaToken()),
                new EntryRequest().email(email.toUpperCase()).captchaToken(validCaptchaToken()));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requests.size());
        try {
            List<CompletableFuture<Void>> calls = requests.stream()
                    .map(request -> CompletableFuture.runAsync(() -> {
                        await(start);
                        entryRequestService.requestEntry(competition.getId(), request);
                    }, executor))
                    .toList();
            start.countDown();
            CompletableFuture.allOf(calls.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(participationRepository.findByCompetition_Id(competition.getId())).hasSize(1);
    }

    @Test
    void aPlayerAlreadyInTheCompetitionRequestingEntryAgainKeepsTheirParticipation() {
        Competition competition = competitionFixtures.publicCompetition();
        User player = userMother.registeredPlayer();
        Participation joined = new Participation();
        joined.setCompetition(competition);
        joined.setUser(player);
        joined.setEmail(player.getEmail());
        joined.setRequestType(RequestType.REQUEST);
        joined.setStatus(ParticipationStatus.IN_COMPETITION);
        joined.setJoinedAt(LocalDate.now().minusDays(2));
        joined = participationRepository.save(joined);
        long sentBefore = sentEmailRepository.count();

        entryRequestService.requestEntry(competition.getId(),
                new EntryRequest().email(player.getEmail().toUpperCase()).captchaToken(validCaptchaToken()));

        assertThat(participationRepository.findByCompetition_Id(competition.getId()))
                .singleElement()
                .satisfies(kept -> {
                    assertThat(kept.getStatus()).isEqualTo(ParticipationStatus.IN_COMPETITION);
                    assertThat(kept.getJoinedAt()).isEqualTo(LocalDate.now().minusDays(2));
                });
        assertThat(sentEmailRepository.count()).isEqualTo(sentBefore + 1);
    }

    private String validCaptchaToken() {
        try {
            Altcha.Challenge challenge = captchaVerifier.createChallenge();
            Altcha.Solution solution = Altcha.solveChallenge(challenge, Altcha.kdf(challenge.parameters().algorithm()));
            return captchaVerifier.encodeToken(challenge, solution);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to solve captcha challenge in test", e);
        }
    }

    private static void await(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
