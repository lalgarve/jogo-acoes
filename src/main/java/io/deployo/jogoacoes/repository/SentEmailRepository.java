package io.deployo.jogoacoes.repository;

import io.deployo.jogoacoes.domain.SentEmail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SentEmailRepository extends JpaRepository<SentEmail, Long> {
}
