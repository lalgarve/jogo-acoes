package dev.leilaalgarve.jogoacoes.competition;

import dev.leilaalgarve.jogoacoes.common.testsupport.CompetitionFixtures;
import dev.leilaalgarve.jogoacoes.common.testsupport.UserMother;
import dev.leilaalgarve.jogoacoes.competition.exception.PlayerNotFoundException;
import dev.leilaalgarve.jogoacoes.competition.exception.PlayerValidationException;
import dev.leilaalgarve.jogoacoes.log.LogRepository;
import dev.leilaalgarve.jogoacoes.log.LogType;
import dev.leilaalgarve.jogoacoes.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

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
 * Spec 05-036: removing a player through a competition the participation doesn't belong to,
 * against the real Postgres of the docker profile. Not {@code @Transactional}: the service's own
 * transaction rolls back on the exception, and the assertions read what was actually committed.
 *
 * <p>Spec 05-057: an invite racing another one for the same e-mail passes the up-front check,
 * then the unique index rejects it and the service reports it as a validation error.
 */
@SpringBootTest
class PlayerManagementServiceIntegrationTest {

    @Autowired
    private PlayerManagementService playerManagementService;

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    private LogRepository logRepository;

    @Autowired
    private CompetitionFixtures competitionFixtures;

    @Autowired
    private UserMother userMother;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void removingAPlayerThroughACompetitionTheyAreNotInFindsNoPlayerAndRemovesNothing() {
        Competition competition1 = competitionFixtures.privateCompetition();
        Competition competition2 = competitionFixtures.privateCompetition();
        Participation playerAInCompetition1 = participationRepository.save(
                emailSentParticipation(competition1, userMother.registeredPlayer()));

        assertThatThrownBy(() -> playerManagementService.removePlayer(
                competition2.getId(), playerAInCompetition1.getId()))
                .isInstanceOf(PlayerNotFoundException.class);

        assertThat(participationRepository.findById(playerAInCompetition1.getId()))
                .get()
                .satisfies(kept -> {
                    assertThat(kept.getCompetition().getId()).isEqualTo(competition1.getId());
                    assertThat(kept.getStatus()).isEqualTo(ParticipationStatus.EMAIL_SENT);
                });
        assertThat(participationRepository.findByCompetition_Id(competition2.getId())).isEmpty();
        assertThat(logRepository.findAll())
                .noneMatch(log -> log.getLogType() == LogType.PARTICIPATION_STATUS_CHANGED
                        && playerAInCompetition1.getId().equals(log.getRelatedObjectId()));
    }

    @Test
    void anInviteRacingAnotherForTheSameEmailIsRejectedByTheUniqueIndex() throws Exception {
        Competition competition = privateCompetitionWithoutInvitees();
        User administrator = userMother.administrator();
        String email = unique("racing-invite");

        // The other invite: its row is written but not committed while this one checks the
        // list, so the check passes and the insert waits on the unique index, then fails.
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<Void> other = CompletableFuture.runAsync(() -> transactionTemplate.executeWithoutResult(status -> {
                Participation participation = new Participation();
                participation.setCompetition(competition);
                participation.setEmail(email);
                participation.setStatus(ParticipationStatus.EMAIL_NOT_SENT);
                participation.setRequestType(RequestType.INVITE);
                participationRepository.saveAndFlush(participation);
                inserted.countDown();
                await(release);
            }), executor);
            await(inserted);

            CompletableFuture<Throwable> invite = CompletableFuture.supplyAsync(() -> {
                authenticateAs(administrator);
                try {
                    playerManagementService.invitePlayers(competition.getId(), List.of(email.toUpperCase()));
                    return null;
                } catch (Throwable failure) {
                    return failure;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }, executor);
            Thread.sleep(500);
            release.countDown();

            other.get(30, TimeUnit.SECONDS);
            assertThat(invite.get(30, TimeUnit.SECONDS)).isInstanceOf(PlayerValidationException.class)
                    .hasMessageContaining("already in this competition");
        } finally {
            executor.shutdownNow();
        }

        assertThat(participationRepository.findByCompetition_Id(competition.getId()))
                .singleElement()
                .satisfies(kept -> assertThat(kept.getEmail()).isEqualTo(email));
    }

    private Competition privateCompetitionWithoutInvitees() {
        return competitionFixtures.privateCompetition();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void authenticateAs(User user) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of()));
        SecurityContextHolder.setContext(context);
    }

    private static Participation emailSentParticipation(Competition competition, User player) {
        Participation participation = new Participation();
        participation.setCompetition(competition);
        participation.setUser(player);
        participation.setEmail(player.getEmail());
        participation.setRequestType(RequestType.INVITE);
        participation.setStatus(ParticipationStatus.EMAIL_SENT);
        participation.setFirstEmailSentDate(LocalDate.now());
        return participation;
    }
}
