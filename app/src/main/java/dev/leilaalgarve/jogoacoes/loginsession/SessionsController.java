package dev.leilaalgarve.jogoacoes.loginsession;

import dev.leilaalgarve.jogoacoes.api.SessionsApi;
import dev.leilaalgarve.jogoacoes.api.model.Session;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.List;

@RestController
public class SessionsController implements SessionsApi {

    private final SessionsService sessionsService;
    private final CurrentUserService currentUserService;

    public SessionsController(SessionsService sessionsService, CurrentUserService currentUserService) {
        this.sessionsService = sessionsService;
        this.currentUserService = currentUserService;
    }

    @Override
    public ResponseEntity<List<Session>> listActiveSessions() {
        List<Session> sessions = sessionsService.listActive(currentUserService.currentUser().getId()).stream()
                .map(this::toApiModel)
                .toList();
        return ResponseEntity.ok(sessions);
    }

    @Override
    public ResponseEntity<Void> revokeSession(Long sessionId) {
        boolean revoked = sessionsService.revoke(sessionId, currentUserService.currentUser().getId());
        return revoked ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }


    private Session toApiModel(LoginSession session) {
        return new Session()
                .id(session.getId())
                .deviceLabel(session.getDeviceId())
                // LocalDateTime everywhere else in this codebase is naive/server-local -- UTC
                // is an arbitrary but harmless choice to satisfy the contract's OffsetDateTime,
                // no timezone concept exists anywhere else to be consistent with.
                .createdAt(session.getCreatedAt().atOffset(ZoneOffset.UTC))
                .current(sessionsService.isCurrent(session));
    }
}
