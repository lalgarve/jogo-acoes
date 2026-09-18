package dev.leilaalgarve.jogoacoes.login;

import dev.leilaalgarve.jogoacoes.link.LoginSession;
import dev.leilaalgarve.jogoacoes.link.LoginSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Spec 05-010: lists/revokes the authenticated player's own {@link LoginSession} rows.
 * Revocation deletes the real HTTP session too (Spring Session JDBC, see der.md's note on
 * {@code http_session_id}) -- marking our own row as ended alone would be cosmetic, since
 * nothing in the {@code SecurityFilterChain} consults it on every request.
 */
@Component
@SuppressWarnings("rawtypes")
public class SessionsService {

    private final LoginSessionRepository loginSessionRepository;
    private final SessionRepository sessionRepository;
    private final HttpServletRequest request;

    public SessionsService(LoginSessionRepository loginSessionRepository, SessionRepository sessionRepository,
                            HttpServletRequest request) {
        this.loginSessionRepository = loginSessionRepository;
        this.sessionRepository = sessionRepository;
        this.request = request;
    }

    public List<LoginSession> listActive(Long userId) {
        return loginSessionRepository.findByUserIdAndEndedAtIsNullOrderByCreatedAtAsc(userId);
    }

    public boolean isCurrent(LoginSession session) {
        return session.getHttpSessionId().equals(request.getSession().getId());
    }

    /** @return {@code false} if no active session with that id belongs to this user -- the
     * caller turns that into a 404 that doesn't reveal which case it was. */
    @SuppressWarnings("unchecked")
    public boolean revoke(Long sessionId, Long userId) {
        Optional<LoginSession> found = loginSessionRepository.findByIdAndUserId(sessionId, userId);
        if (found.isEmpty()) {
            return false;
        }
        LoginSession session = found.get();
        sessionRepository.deleteById(session.getHttpSessionId());
        session.setEndedAt(LocalDateTime.now());
        loginSessionRepository.save(session);
        return true;
    }
}
