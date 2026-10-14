package dev.leilaalgarve.jogoacoes.emailservice.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Recreates on SES every template that only the database still has (spec 05-044) -- what a
 * LocalStack restart, or a template deleted from SES outside this service, leaves behind.
 *
 * <p>Runs in the background once the service is up, so the service starts and answers requests
 * even with SES down. A round that fails is logged and tried again later, waiting longer each
 * time up to {@code max-delay}, never giving up; after the first round with no failure it stops.
 * It only creates what is missing ({@link SesTemplateClient#createIfAbsent}), never overwrites:
 * a PUT may have changed a template after this round read the row. A template SES has with no row
 * here is left alone and logged.
 */
@Component
public class SesTemplateReconciler {

    private static final Logger log = LoggerFactory.getLogger(SesTemplateReconciler.class);
    private static final String NAMESPACE_SEPARATOR = "__";

    private final EmailTemplateRepository repository;
    private final SesTemplateClient sesTemplateClient;
    private final TaskScheduler taskScheduler;
    private final Duration initialDelay;
    private final Duration maxDelay;

    public SesTemplateReconciler(EmailTemplateRepository repository, SesTemplateClient sesTemplateClient,
                                 TaskScheduler taskScheduler,
                                 @Value("${email.ses.reconciliation.initial-delay:1s}") Duration initialDelay,
                                 @Value("${email.ses.reconciliation.max-delay:5m}") Duration maxDelay) {
        this.repository = repository;
        this.sesTemplateClient = sesTemplateClient;
        this.taskScheduler = taskScheduler;
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
    }

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        taskScheduler.schedule(() -> runRound(initialDelay), Instant.now());
    }

    private void runRound(Duration nextDelay) {
        List<String> failures = reconcile();
        if (failures.isEmpty()) {
            return;
        }
        log.warn("SES template reconciliation failed, trying again in {}: {}", nextDelay, failures);
        Duration delayAfterThat = nextDelay.multipliedBy(2).compareTo(maxDelay) > 0 ? maxDelay : nextDelay.multipliedBy(2);
        taskScheduler.schedule(() -> runRound(delayAfterThat), Instant.now().plus(nextDelay));
    }

    /** One full round; returns what failed, empty when SES has every template the database has. */
    List<String> reconcile() {
        Set<String> onSes;
        try {
            onSes = sesTemplateClient.listTemplateNames();
        } catch (RuntimeException e) {
            return List.of("ListTemplates: " + e.getMessage());
        }

        List<String> failures = new ArrayList<>();
        Set<String> inDatabase = new HashSet<>();
        for (EmailTemplate template : repository.findAll()) {
            inDatabase.add(template.getSesTemplateName());
            if (onSes.contains(template.getSesTemplateName())) {
                continue;
            }
            try {
                sesTemplateClient.createIfAbsent(template.getSesTemplateName(), template.getSubject(), template.getBody());
                log.info("Recreated template {} on SES from the database", template.getSesTemplateName());
            } catch (RuntimeException e) {
                failures.add(template.getSesTemplateName() + ": " + e.getMessage());
            }
        }

        onSes.stream()
                .filter(name -> name.contains(NAMESPACE_SEPARATOR) && !inDatabase.contains(name))
                .forEach(name -> log.warn("Template {} is on SES with no record in the database; left as is", name));
        return failures;
    }
}
