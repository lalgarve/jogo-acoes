package dev.leilaalgarve.jogoacoes.emailservice.disposable;

import dev.leilaalgarve.jogoacoes.emailservice.api.model.DisposableDomainSourceCreateRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Registers and lists the repositories of the lists (spec 05-048). */
@Service
public class DisposableDomainSourceService {

    private final DisposableDomainSourceRepository sources;
    private final DisposableDomainImportService imports;
    private final JdbcTemplate jdbcTemplate;

    public DisposableDomainSourceService(DisposableDomainSourceRepository sources, DisposableDomainImportService imports,
                                         JdbcTemplate jdbcTemplate) {
        this.sources = sources;
        this.imports = imports;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Format (http/https, description present) is checked by the contract's bean validation.
     *
     * @throws DisposableDomainSourceAlreadyExistsException when the URL is already registered
     */
    @Transactional
    public dev.leilaalgarve.jogoacoes.emailservice.api.model.DisposableDomainSource create(
            DisposableDomainSourceCreateRequest request) {
        String url = request.getUrl().strip();
        if (sources.existsByUrl(url)) {
            throw new DisposableDomainSourceAlreadyExistsException(url);
        }
        DisposableDomainSource saved = sources.save(new DisposableDomainSource(
                url, request.getDescription().strip(), LocalDateTime.now(ZoneOffset.UTC)));
        return toApi(saved);
    }

    public List<dev.leilaalgarve.jogoacoes.emailservice.api.model.DisposableDomainSource> list() {
        return sources.findAll(Sort.by("id")).stream().map(this::toApi).toList();
    }

    private dev.leilaalgarve.jogoacoes.emailservice.api.model.DisposableDomainSource toApi(DisposableDomainSource source) {
        return new dev.leilaalgarve.jogoacoes.emailservice.api.model.DisposableDomainSource()
                .id(source.getId())
                .url(source.getUrl())
                .description(source.getDescription())
                .lastDownloadedAt(toOffset(source.getLastDownloadedAt()))
                .lastUpdatedAt(toOffset(source.getLastUpdatedAt()))
                .domainCount(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM disposable_domain WHERE source_id = ?", Long.class, source.getId()))
                .lastImport(imports.lastImportOf(source.getId()).orElse(null));
    }

    private static OffsetDateTime toOffset(LocalDateTime utc) {
        return utc == null ? null : utc.atOffset(ZoneOffset.UTC);
    }
}
