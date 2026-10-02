package dev.leilaalgarve.jogoacoes.emailservice.template;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long> {

    Optional<EmailTemplate> findByClientIdAndName(String clientId, String name);

    List<EmailTemplate> findAllByClientId(String clientId);

    boolean existsByClientIdAndName(String clientId, String name);
}
