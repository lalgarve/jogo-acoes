package dev.leilaalgarve.jogoacoes.link;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface LinkRecordRepository extends JpaRepository<LinkRecord, Long> {

    Optional<LinkRecord> findByToken(String token);

    List<LinkRecord> findByUserIdAndUsedAtIsNullAndInvalidatedAtIsNull(Long userId);

    Optional<LinkRecord> findFirstByUserIdAndUsedAtIsNullAndInvalidatedAtIsNullOrderByIdDesc(Long userId);

    List<LinkRecord> findByServiceKeyAndEmailIgnoreCaseAndUsedAtIsNullAndInvalidatedAtIsNullAndExpiresAtAfter(
            String serviceKey, String email, LocalDateTime expiresAfter);
}
