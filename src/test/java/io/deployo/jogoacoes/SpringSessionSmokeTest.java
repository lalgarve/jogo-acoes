package io.deployo.jogoacoes;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves Spring Session actually writes to spring_session/spring_session_attributes
 * (not just an in-memory fallback) — checked directly via JdbcTemplate, independent of the
 * session object still held in memory. Raw SessionRepository: the concrete session type
 * (JdbcIndexedSessionRepository.JdbcSession) is package-private in spring-session-jdbc.
 */
@SpringBootTest
@SuppressWarnings({"unchecked", "rawtypes"})
class SpringSessionSmokeTest {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void sessionIsPersistedToTheDatabaseNotJustKeptInMemory() {
        Session session = sessionRepository.createSession();
        session.setAttribute("greeting", "ola");
        sessionRepository.save(session);

        Integer rowCount = jdbcTemplate.queryForObject(
                "select count(*) from spring_session where session_id = ?",
                Integer.class, session.getId());
        assertThat(rowCount).isEqualTo(1);

        Session reloaded = sessionRepository.findById(session.getId());
        Object greeting = reloaded.getAttribute("greeting");
        assertThat(greeting).isEqualTo("ola");
    }
}
