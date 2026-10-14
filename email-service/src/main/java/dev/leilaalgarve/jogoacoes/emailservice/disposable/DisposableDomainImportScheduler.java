package dev.leilaalgarve.jogoacoes.emailservice.disposable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Spec 05-048: the daily import of every repository, on {@code email.disposable-domains.import-cron}
 * -- "-" (the default, and docker's) switches it off; staging/production set it in config-repo.
 */
@Configuration
@EnableScheduling
public class DisposableDomainImportScheduler {

    private static final Logger log = LoggerFactory.getLogger(DisposableDomainImportScheduler.class);

    private final DisposableDomainImportService importService;

    public DisposableDomainImportScheduler(DisposableDomainImportService importService) {
        this.importService = importService;
    }

    @Scheduled(cron = "${email.disposable-domains.import-cron:-}")
    public void importEveryRepository() {
        try {
            log.info("Scheduled import of the disposable domain lists: {} started",
                    importService.start(null).getImports().size());
        } catch (DisposableDomainImportAlreadyRunningException e) {
            log.warn("Scheduled import of the disposable domain lists skipped: {}", e.getMessage());
        }
    }
}
