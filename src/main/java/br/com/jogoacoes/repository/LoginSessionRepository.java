package br.com.jogoacoes.repository;

import br.com.jogoacoes.domain.LoginSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginSessionRepository extends JpaRepository<LoginSession, Long> {
}
