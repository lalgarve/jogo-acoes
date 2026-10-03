package dev.leilaalgarve.jogoacoes.log;

import dev.leilaalgarve.jogoacoes.login.UserRepository;

import dev.leilaalgarve.jogoacoes.log.Log;
import dev.leilaalgarve.jogoacoes.log.LogType;
import dev.leilaalgarve.jogoacoes.login.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

import static dev.leilaalgarve.jogoacoes.common.testsupport.TestEmails.fixed;
import static org.assertj.core.api.Assertions.assertThat;

// Guards against @DataJpaTest's default behavior of swapping in an embedded database --
// not that one is even on the classpath to swap in (specs/05-028-testes-exigem-docker-real/
// plan.md: no H2 dependency anywhere in this project), but explicit is cheaper than relying on
// that absence. flyway.locations points at the Postgres-specific migrations (GRANT/REVOKE),
// which only work against the real PostgreSQL this is meant to keep using.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest
class LogRepositoryTest {

    @Autowired
    private LogRepository logRepository;

    @Autowired
    private UserRepository userRepository;

    private User user1;
    private User user2;

    private Log log1CompetitionUser1Day1;
    private Log log2LoginLinkUser1Day2;
    private Log log3CompetitionUser2Day3;
    private Log log4ParticipationNoUserDay4;

    @BeforeEach
    void setUp() {
        user1 = userRepository.save(newUser(fixed("alice")));
        user2 = userRepository.save(newUser(fixed("bob")));

        log1CompetitionUser1Day1 = logRepository.save(
                newLog(1L, user1, LogType.COMPETITION_CREATED, LocalDateTime.parse("2026-08-01T10:00:00")));
        log2LoginLinkUser1Day2 = logRepository.save(
                newLog(2L, user1, LogType.LOGIN_LINK_ISSUED, LocalDateTime.parse("2026-08-02T10:00:00")));
        log3CompetitionUser2Day3 = logRepository.save(
                newLog(3L, user2, LogType.COMPETITION_CREATED, LocalDateTime.parse("2026-08-03T10:00:00")));
        log4ParticipationNoUserDay4 = logRepository.save(
                newLog(4L, null, LogType.PARTICIPATION_STATUS_CHANGED, LocalDateTime.parse("2026-08-04T10:00:00")));
    }

    // No filter / log-type-only filter below can't scope to this test's own rows the way
    // filtersByUser/combinesLogTypeAndUserFilters do -- LOG is insert-only and shared by every
    // test class against the same real PostgreSQL in the same Maven run (specs/05-028-testes-
    // exigem-docker-real/plan.md; see also Issue #41), so another test's committed row of the
    // same type can legitimately also match. Assert containment (this test's own four rows are
    // in there), not exact membership (only these four rows exist).
    @Test
    void returnsEverythingWhenNoFilterIsGiven() {
        Page<Log> result = logRepository.findFiltered(
            null, null, null, null, PageRequest.of(0, Integer.MAX_VALUE));

        assertThat(result.getContent()).extracting(Log::getId).contains(
            log1CompetitionUser1Day1.getId(), log2LoginLinkUser1Day2.getId(),
            log3CompetitionUser2Day3.getId(), log4ParticipationNoUserDay4.getId());
    }

    @Test
    void filtersByLogType() {
        Page<Log> result = logRepository.findFiltered(
            LogType.COMPETITION_CREATED, null, null, null, PageRequest.of(0, Integer.MAX_VALUE));

        assertThat(result.getContent()).extracting(Log::getId)
            .contains(log1CompetitionUser1Day1.getId(), log3CompetitionUser2Day3.getId());
    }

    @Test
    void filtersByUser() {
        Page<Log> result = logRepository.findFiltered(null, user1.getId(), null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).containsExactlyInAnyOrder(log1CompetitionUser1Day1, log2LoginLinkUser1Day2);
    }

    @Test
    void filtersByDateRange() {
        Page<Log> result = logRepository.findFiltered(
                null, null, LocalDateTime.parse("2026-08-02T00:00:00"), LocalDateTime.parse("2026-08-03T23:59:59"), PageRequest.of(0, 10));

        assertThat(result.getContent()).containsExactlyInAnyOrder(log2LoginLinkUser1Day2, log3CompetitionUser2Day3);
    }

    @Test
    void combinesLogTypeAndUserFilters() {
        Page<Log> result = logRepository.findFiltered(
                LogType.COMPETITION_CREATED, user1.getId(), null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).containsExactly(log1CompetitionUser1Day1);
    }

    private static User newUser(String email) {
        User user = new User();
        user.setName("Test User");
        user.setEmail(email);
        user.setRegistered(true);
        return user;
    }

    private static Log newLog(long relatedObjectId, User user, LogType logType, LocalDateTime createdAt) {
        Log log = new Log();
        log.setRelatedObjectId(relatedObjectId);
        log.setUser(user);
        log.setLogType(logType);
        log.setCreatedAt(createdAt);
        log.setMessage(logType + " #" + relatedObjectId);
        return log;
    }
}
