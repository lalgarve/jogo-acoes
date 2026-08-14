package br.com.jogoacoes.repository;

import br.com.jogoacoes.domain.SentEmail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SentEmailRepository extends JpaRepository<SentEmail, Long> {
}
