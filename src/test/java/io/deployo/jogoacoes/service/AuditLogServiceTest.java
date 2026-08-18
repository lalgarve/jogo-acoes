package io.deployo.jogoacoes.service;

import io.deployo.jogoacoes.domain.Log;
import io.deployo.jogoacoes.domain.LogType;
import io.deployo.jogoacoes.domain.User;
import io.deployo.jogoacoes.repository.LogRepository;
import io.deployo.jogoacoes.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// Without this, @DataJpaTest swaps in an embedded H2 database regardless of the active
// profile, but flyway.locations still points at the Postgres-specific migrations (with
// GRANT/REVOKE) -- those fail against H2. Keep using whatever datasource the active
// profile configures (H2 in sandbox, real Postgres in docker/CI).
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest
class AuditLogServiceTest {

    @Autowired
    private LogRepository logRepository;

    @Autowired
    private UserRepository userRepository;

    private AuditLogService auditLogService;

    @Test
    void recordsAnEntryWithTheActingUser() {
        auditLogService = new AuditLogService(logRepository);
        User actor = userRepository.save(newUser("alice@example.com"));

        auditLogService.record(LogType.COMPETITION_CREATED, 42L, actor, "Competition \"Test\" created");

        List<Log> logs = logRepository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getLogType()).isEqualTo(LogType.COMPETITION_CREATED);
        assertThat(logs.get(0).getRelatedObjectId()).isEqualTo(42L);
        assertThat(logs.get(0).getUser().getId()).isEqualTo(actor.getId());
        assertThat(logs.get(0).getMessage()).isEqualTo("Competition \"Test\" created");
        assertThat(logs.get(0).getCreatedAt()).isNotNull();
    }

    @Test
    void recordsAnEntryWithoutAnActorWhenTheEventIsNotUserInitiated() {
        auditLogService = new AuditLogService(logRepository);

        auditLogService.record(LogType.LOGIN_LINK_ISSUED, 7L, null, "Login link issued to bob@example.com");

        List<Log> logs = logRepository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getUser()).isNull();
        assertThat(logs.get(0).getLogType()).isEqualTo(LogType.LOGIN_LINK_ISSUED);
    }

    private static User newUser(String email) {
        User user = new User();
        user.setName("Test User");
        user.setEmail(email);
        user.setRegistered(true);
        return user;
    }
}
