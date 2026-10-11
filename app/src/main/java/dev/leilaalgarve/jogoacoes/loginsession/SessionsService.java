package dev.leilaalgarve.jogoacoes.loginsession;

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
    public boolean revoke(Long sessionId, Long userId) {
        Optional<LoginSession> found = loginSessionRepository.findByIdAndUserId(sessionId, userId);
        if (found.isEmpty()) {
            return false;
        }
        end(found.get());
        return true;
    }

    /**
     * Spec 05-037: ends the session making this request. An authenticated request always has a
     * {@link LoginSession} in the normal flow (every login goes through
     * {@code LoginLinkSessionService.establish}); if one is missing anyway, the HTTP session is
     * still deleted -- the caller asked to be logged out, and failing would leave the device
     * authenticated.
     */
    @SuppressWarnings("unchecked")
    public void endCurrent() {
        String httpSessionId = request.getSession().getId();
        loginSessionRepository.findByHttpSessionIdAndEndedAtIsNull(httpSessionId)
                .ifPresentOrElse(this::end, () -> sessionRepository.deleteById(httpSessionId));
    }

    @SuppressWarnings("unchecked")
    private void end(LoginSession session) {
        sessionRepository.deleteById(session.getHttpSessionId());
        session.setEndedAt(LocalDateTime.now());
        loginSessionRepository.save(session);
    }
}
