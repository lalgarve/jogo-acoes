package dev.leilaalgarve.jogoacoes.emailservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A recipient domain on the whitelist (spec 05-047): known to accept e-mail, never looked up in
 * DNS again. Written only through {@link ApprovedDomainRepository#approve}.
 */
@Entity
@Table(name = "approved_domain")
public class ApprovedDomain {

    /** Set by the migration, not by DNS. */
    public static final String SEED = "SEED";

    @Id
    private String domain;

    /** The record that approved it (MX, A, AAAA), or {@link #SEED}. */
    @Column(name = "approved_by", nullable = false)
    private String approvedBy;

    @Column(name = "approved_at", nullable = false)
    private Instant approvedAt;

    protected ApprovedDomain() {
        // JPA
    }

    public String getDomain() {
        return domain;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }
}
