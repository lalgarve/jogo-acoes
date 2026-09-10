package io.deployo.jogoacoes.repository;

import io.deployo.jogoacoes.domain.SentEmail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SentEmailRepository extends JpaRepository<SentEmail, Long> {

    Optional<SentEmail> findByLink(String link);
}
