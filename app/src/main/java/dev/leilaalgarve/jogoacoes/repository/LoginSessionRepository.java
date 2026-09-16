package dev.leilaalgarve.jogoacoes.repository;

import dev.leilaalgarve.jogoacoes.domain.LoginSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoginSessionRepository extends JpaRepository<LoginSession, Long> {

    List<LoginSession> findByUser_IdAndEndedAtIsNullOrderByCreatedAtAsc(Long userId);
}
