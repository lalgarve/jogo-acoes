package dev.leilaalgarve.jogoacoes.link;

import dev.leilaalgarve.jogoacoes.link.LoginSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoginSessionRepository extends JpaRepository<LoginSession, Long> {

    List<LoginSession> findByUserIdAndEndedAtIsNullOrderByCreatedAtAsc(Long userId);
}
