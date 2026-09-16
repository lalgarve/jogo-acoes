package dev.leilaalgarve.jogoacoes.log;

import dev.leilaalgarve.jogoacoes.log.Log;
import dev.leilaalgarve.jogoacoes.log.LogType;
import dev.leilaalgarve.jogoacoes.login.User;
import dev.leilaalgarve.jogoacoes.log.LogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuditLogService {

    private final LogRepository logRepository;

    public AuditLogService(LogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /** actor may be null -- not every auditable event is initiated by a signed-in user. */
    @Transactional
    public void record(LogType type, Long relatedObjectId, User actor, String message) {
        Log log = new Log();
        log.setLogType(type);
        log.setRelatedObjectId(relatedObjectId);
        log.setUser(actor);
        log.setMessage(message);
        log.setCreatedAt(LocalDateTime.now());
        logRepository.save(log);
    }
}
