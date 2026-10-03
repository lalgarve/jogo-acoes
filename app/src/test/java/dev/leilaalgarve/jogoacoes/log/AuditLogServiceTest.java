package dev.leilaalgarve.jogoacoes.log;

import dev.leilaalgarve.jogoacoes.log.Log;
import dev.leilaalgarve.jogoacoes.log.LogRepository;
import dev.leilaalgarve.jogoacoes.log.LogType;
import dev.leilaalgarve.jogoacoes.user.User;
import dev.leilaalgarve.jogoacoes.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static dev.leilaalgarve.jogoacoes.common.testsupport.TestEmails.unique;
import static org.assertj.core.api.Assertions.assertThat;

// Guards against @DataJpaTest's default behavior of swapping in an embedded database --
// not that one is even on the classpath to swap in (specs/05-028-testes-exigem-docker-real/
// plan.md: no H2 dependency anywhere in this project), but explicit is cheaper than relying on
// that absence. flyway.locations points at the Postgres-specific migrations (GRANT/REVOKE),
// which only work against the real PostgreSQL this is meant to keep using.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest
class AuditLogServiceTest {

    @Autowired
    private LogRepository logRepository;

    @Autowired
    private UserRepository userRepository;

    private AuditLogService auditLogService;

    // LOG is insert-only (no UPDATE/DELETE grant in production, see memory/constitution.md)
    // and lives in the same real PostgreSQL database shared by every test class in the same
    // Maven run -- Cucumber's @SpringBootTest scenarios hit real HTTP endpoints and commit for
    // real, unlike this @DataJpaTest's own rolled-back transaction. So the table can already
    // hold rows from other tests by the time these run; assertions below isolate this test's
    // own row instead of assuming the table starts empty.
    @Test
    void recordsAnEntryWithTheActingUser() {
        auditLogService = new AuditLogService(logRepository);
        User actor = userRepository.save(newUser(unique("alice")));

        auditLogService.record(LogType.COMPETITION_CREATED, 42L, actor, "Competition \"Test\" created");

        // Filtering by this test's own freshly created user isolates its log entry from any
        // rows left by other tests -- no pre-existing row can reference a user id that didn't
        // exist before this test ran.
        Page<Log> logs = logRepository.findFiltered(null, actor.getId(), null, null, PageRequest.of(0, 10));
        assertThat(logs.getContent()).hasSize(1);
        Log log = logs.getContent().get(0);
        assertThat(log.getLogType()).isEqualTo(LogType.COMPETITION_CREATED);
        assertThat(log.getRelatedObjectId()).isEqualTo(42L);
        assertThat(log.getUser().getId()).isEqualTo(actor.getId());
        assertThat(log.getMessage()).isEqualTo("Competition \"Test\" created");
        assertThat(log.getCreatedAt()).isNotNull();
    }

    @Test
    void recordsAnEntryWithoutAnActorWhenTheEventIsNotUserInitiated() {
        auditLogService = new AuditLogService(logRepository);
        long before = logRepository.count();
        String message = "Login link issued to " + unique("bob");

        auditLogService.record(LogType.LOGIN_LINK_ISSUED, 7L, null, message);

        assertThat(logRepository.count()).isEqualTo(before + 1);
        Log log = logRepository.findAll().stream()
                .filter(candidate -> message.equals(candidate.getMessage()))
                .findFirst()
                .orElseThrow();
        assertThat(log.getUser()).isNull();
        assertThat(log.getLogType()).isEqualTo(LogType.LOGIN_LINK_ISSUED);
    }

    private static User newUser(String email) {
        User user = new User();
        user.setName("Test User");
        user.setEmail(email);
        user.setRegistered(true);
        return user;
    }
}
