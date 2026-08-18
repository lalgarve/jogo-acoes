package io.deployo.jogoacoes.repository;

import io.deployo.jogoacoes.domain.LoginSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginSessionRepository extends JpaRepository<LoginSession, Long> {
}
