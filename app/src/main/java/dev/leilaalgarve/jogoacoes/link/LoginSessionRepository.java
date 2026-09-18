package dev.leilaalgarve.jogoacoes.link;

import dev.leilaalgarve.jogoacoes.link.LoginSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LoginSessionRepository extends JpaRepository<LoginSession, Long> {

    List<LoginSession> findByUserIdAndEndedAtIsNullOrderByCreatedAtAsc(Long userId);

    // Filters by owner in the query itself (spec 05-010's revocation) -- a session id that
    // exists but belongs to someone else comes back empty, same as a nonexistent id, which is
    // exactly the "don't reveal whether it exists" 404 the endpoint needs.
    Optional<LoginSession> findByIdAndUserId(Long id, Long userId);
}
