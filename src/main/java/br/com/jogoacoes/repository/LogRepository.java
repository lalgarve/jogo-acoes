package br.com.jogoacoes.repository;

import br.com.jogoacoes.domain.Log;
import br.com.jogoacoes.domain.LogType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface LogRepository extends JpaRepository<Log, Long> {

    @Query("""
            SELECT l FROM Log l
            WHERE (:logType IS NULL OR l.logType = :logType)
              AND (:userId IS NULL OR l.user.id = :userId)
              AND (:from IS NULL OR l.createdAt >= :from)
              AND (:to IS NULL OR l.createdAt <= :to)
            """)
    Page<Log> findFiltered(
            @Param("logType") LogType logType,
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);
}
