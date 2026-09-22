package dev.leilaalgarve.jogoacoes.email;

import dev.leilaalgarve.jogoacoes.email.SentEmail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SentEmailRepository extends JpaRepository<SentEmail, Long> {

    Optional<SentEmail> findByLink(String link);

    // Spec 05-014 (blackbox environment): the only way, outside the Java process, to read the
    // link of an e-mail sent during a test -- see BlackboxController.
    Optional<SentEmail> findTopByEmailOrderBySentAtDesc(String email);
}
