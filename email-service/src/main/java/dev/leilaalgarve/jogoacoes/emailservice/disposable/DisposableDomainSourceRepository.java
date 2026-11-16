package dev.leilaalgarve.jogoacoes.emailservice.disposable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * The repositories of the lists (spec 05-048). The three updates are the import job's (spec
 * 05-063): native SQL, the same the tasklets used to write themselves; times are UTC wall-clock.
 */
public interface DisposableDomainSourceRepository extends JpaRepository<DisposableDomainSource, Long> {

    boolean existsByUrl(String url);

    @Modifying
    @Transactional
    @Query(value = "UPDATE disposable_domain_source SET last_downloaded_at = :at WHERE id = :id", nativeQuery = true)
    void markDownloaded(@Param("id") long id, @Param("at") LocalDateTime at);

    /** A domain came or went: the database changed now, with the list of this SHA-256. */
    @Modifying
    @Transactional
    @Query(value = "UPDATE disposable_domain_source SET last_updated_at = :at, content_sha256 = :sha256 WHERE id = :id",
            nativeQuery = true)
    void markUpdated(@Param("id") long id, @Param("at") LocalDateTime at, @Param("sha256") String sha256);

    /** Same domains as before, from a list with another SHA-256 (comments or order changed). */
    @Modifying
    @Transactional
    @Query(value = "UPDATE disposable_domain_source SET content_sha256 = :sha256 WHERE id = :id", nativeQuery = true)
    void updateContentHash(@Param("id") long id, @Param("sha256") String sha256);
}
