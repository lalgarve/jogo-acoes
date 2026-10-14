package dev.leilaalgarve.jogoacoes.emailservice.disposable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * A repository of a disposable e-mail domain list (spec 05-048). Times are UTC wall-clock, the
 * same way the import job writes them with JDBC. The job owns the download/update times and the
 * content hash (content_sha256, not mapped here); this entity only creates rows and reads them.
 */
@Entity
@Table(name = "disposable_domain_source")
public class DisposableDomainSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 2048)
    private String url;

    @Column(nullable = false)
    private String description;

    @Column(name = "last_downloaded_at")
    private LocalDateTime lastDownloadedAt;

    @Column(name = "last_updated_at")
    private LocalDateTime lastUpdatedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected DisposableDomainSource() {
        // JPA
    }

    public DisposableDomainSource(String url, String description, LocalDateTime createdAt) {
        this.url = url;
        this.description = description;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getLastDownloadedAt() {
        return lastDownloadedAt;
    }

    public LocalDateTime getLastUpdatedAt() {
        return lastUpdatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
