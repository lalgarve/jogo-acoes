package dev.leilaalgarve.jogoacoes.emailservice.template;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

    Optional<EmailTemplate> findByClientIdAndName(String clientId, String name);

    List<EmailTemplate> findAllByClientId(String clientId);

    boolean existsByClientIdAndName(String clientId, String name);

    /**
     * Spec 05-046: the template, locked until the caller's transaction ends -- a burst of
     * TEMPLATE_MISSING errors for the same template recreates it once, not once per error.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM EmailTemplate t WHERE t.id = :id")
    Optional<EmailTemplate> lockById(@Param("id") Long id);
}
