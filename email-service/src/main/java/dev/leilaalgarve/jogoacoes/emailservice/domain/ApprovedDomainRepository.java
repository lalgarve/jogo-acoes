package dev.leilaalgarve.jogoacoes.emailservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface ApprovedDomainRepository extends JpaRepository<ApprovedDomain, String> {

    /**
     * Two sends to the same new domain at once both approve it: the second insert does nothing
     * instead of failing. Its own transaction, so the approval holds even when the send that
     * triggered it fails afterwards -- DNS did answer.
     */
    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query(value = "INSERT INTO approved_domain (domain, approved_by, approved_at)"
            + " VALUES (:domain, :approvedBy, :approvedAt) ON CONFLICT (domain) DO NOTHING",
            nativeQuery = true)
    void approve(@Param("domain") String domain, @Param("approvedBy") String approvedBy,
                 @Param("approvedAt") Instant approvedAt);

    @Modifying
    @Transactional
    @Query("DELETE FROM ApprovedDomain d WHERE d.approvedBy <> '" + ApprovedDomain.SEED + "'")
    void deleteAllNotSeeded();
}
