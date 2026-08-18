package io.deployo.jogoacoes.repository;

import io.deployo.jogoacoes.domain.Log;
import io.deployo.jogoacoes.domain.LogType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface LogRepository extends JpaRepository<Log, Long> {

    // :from/:to are cast explicitly because PostgreSQL's JDBC driver can't infer a bind
    // parameter's type from a bare "? IS NULL" check alone -- unlike :logType (typed via
    // the @Enumerated string conversion) and :userId, nothing else in the query ties the
    // first occurrence of :from/:to to a column type, so Postgres rejects the prepared
    // statement with "could not determine data type of parameter". H2 tolerates this; only
    // a real Postgres run (CI's docker profile) surfaces it.
    @Query("""
            SELECT l FROM Log l
            WHERE (:logType IS NULL OR l.logType = :logType)
              AND (:userId IS NULL OR l.user.id = :userId)
              AND (CAST(:from AS timestamp) IS NULL OR l.createdAt >= :from)
              AND (CAST(:to AS timestamp) IS NULL OR l.createdAt <= :to)
            """)
    Page<Log> findFiltered(
            @Param("logType") LogType logType,
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);
}
