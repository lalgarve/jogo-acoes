package io.deployo.jogoacoes.repository;

import io.deployo.jogoacoes.domain.Log;
import io.deployo.jogoacoes.domain.LogType;
import io.deployo.jogoacoes.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// Without this, @DataJpaTest swaps in an embedded H2 database regardless of the active
// profile, but flyway.locations still points at the Postgres-specific migrations (with
// GRANT/REVOKE) -- those fail against H2. Keep using whatever datasource the active
// profile configures (H2 in sandbox, real Postgres in docker/CI).
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
        user1 = userRepository.save(newUser("alice@example.com"));
        user2 = userRepository.save(newUser("bob@example.com"));

        log1CompetitionUser1Day1 = logRepository.save(
                newLog(1L, user1, LogType.COMPETITION_CREATED, LocalDateTime.parse("2026-08-01T10:00:00")));
        log2LoginLinkUser1Day2 = logRepository.save(
                newLog(2L, user1, LogType.LOGIN_LINK_ISSUED, LocalDateTime.parse("2026-08-02T10:00:00")));
        log3CompetitionUser2Day3 = logRepository.save(
                newLog(3L, user2, LogType.COMPETITION_CREATED, LocalDateTime.parse("2026-08-03T10:00:00")));
        log4ParticipationNoUserDay4 = logRepository.save(
                newLog(4L, null, LogType.PARTICIPATION_STATUS_CHANGED, LocalDateTime.parse("2026-08-04T10:00:00")));
    }

    @Test
    void returnsEverythingWhenNoFilterIsGiven() {
        Page<Log> result = logRepository.findFiltered(null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).containsExactlyInAnyOrder(
                log1CompetitionUser1Day1, log2LoginLinkUser1Day2, log3CompetitionUser2Day3, log4ParticipationNoUserDay4);
    }

    @Test
    void filtersByLogType() {
        Page<Log> result = logRepository.findFiltered(LogType.COMPETITION_CREATED, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).containsExactlyInAnyOrder(log1CompetitionUser1Day1, log3CompetitionUser2Day3);
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
