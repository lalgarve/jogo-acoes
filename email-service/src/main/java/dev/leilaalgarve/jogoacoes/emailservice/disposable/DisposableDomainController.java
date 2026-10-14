package dev.leilaalgarve.jogoacoes.emailservice.disposable;

import dev.leilaalgarve.jogoacoes.emailservice.api.DisposableDomainsApi;
import dev.leilaalgarve.jogoacoes.emailservice.api.model.DisposableDomainImport;
import dev.leilaalgarve.jogoacoes.emailservice.api.model.DisposableDomainImportRequest;
import dev.leilaalgarve.jogoacoes.emailservice.api.model.DisposableDomainImportStarted;
import dev.leilaalgarve.jogoacoes.emailservice.api.model.DisposableDomainSource;
import dev.leilaalgarve.jogoacoes.emailservice.api.model.DisposableDomainSourceCreateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Spec 05-048: operations' routes for the disposable-domain lists (permission disposable-email:admin). */
@RestController
public class DisposableDomainController implements DisposableDomainsApi {

    private final DisposableDomainSourceService sourceService;
    private final DisposableDomainImportService importService;

    public DisposableDomainController(DisposableDomainSourceService sourceService,
                                      DisposableDomainImportService importService) {
        this.sourceService = sourceService;
        this.importService = importService;
    }

    @Override
    public ResponseEntity<List<DisposableDomainSource>> listDisposableDomainSources() {
        return ResponseEntity.ok(sourceService.list());
    }

    @Override
    public ResponseEntity<DisposableDomainSource> createDisposableDomainSource(
            DisposableDomainSourceCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sourceService.create(request));
    }

    @Override
    public ResponseEntity<DisposableDomainImportStarted> startDisposableDomainImport(
            DisposableDomainImportRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(importService.start(request == null ? null : request.getSourceId()));
    }

    @Override
    public ResponseEntity<DisposableDomainImport> getDisposableDomainImport(Long executionId) {
        return ResponseEntity.ok(importService.get(executionId));
    }
}
