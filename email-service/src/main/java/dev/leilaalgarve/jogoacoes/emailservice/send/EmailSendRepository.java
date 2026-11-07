package dev.leilaalgarve.jogoacoes.emailservice.send;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EmailSendRepository extends JpaRepository<EmailSend, UUID> {

    /**
     * Spec 05-046: the PENDING sends due by {@code now}, locked for the caller's transaction.
     * {@code SKIP LOCKED}: rows another instance is already publishing are left to it.
     */
    @Query(value = "SELECT * FROM email_send WHERE status = 'PENDING' AND next_attempt_at <= :now"
            + " ORDER BY next_attempt_at LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<EmailSend> lockDue(@Param("now") Instant now, @Param("limit") int limit);
}
