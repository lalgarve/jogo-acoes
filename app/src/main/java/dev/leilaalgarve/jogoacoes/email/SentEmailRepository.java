package dev.leilaalgarve.jogoacoes.email;

import dev.leilaalgarve.jogoacoes.email.SentEmail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SentEmailRepository extends JpaRepository<SentEmail, Long> {

    Optional<SentEmail> findByLink(String link);
}
