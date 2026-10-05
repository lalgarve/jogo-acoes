package dev.leilaalgarve.jogoacoes.email.client;

import dev.leilaalgarve.jogoacoes.email.client.api.TemplatesApi;
import dev.leilaalgarve.jogoacoes.email.client.api.model.EmailTemplate;
import dev.leilaalgarve.jogoacoes.email.client.api.model.EmailTemplateCreateRequest;
import dev.leilaalgarve.jogoacoes.email.client.api.model.EmailTemplateUpdateRequest;
import dev.leilaalgarve.jogoacoes.email.client.api.model.TemplatePreviewRequest;
import dev.leilaalgarve.jogoacoes.email.client.api.model.TemplatePreviewResponse;
import dev.leilaalgarve.jogoacoes.email.exception.EmailServiceAuthenticationException;
import dev.leilaalgarve.jogoacoes.email.exception.EmailServiceRejectedException;
import dev.leilaalgarve.jogoacoes.email.exception.EmailServiceUnavailableException;
import feign.FeignException;
import feign.RetryableException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The only class that talks to the Feign client generated from docs/openapi-email-service.yaml
 * (spec 05-034, ArchitectureTest): the rest of app sees app types and app exceptions, never
 * Feign or email-service's DTOs.
 *
 * <p>Retries only idempotent calls (GET, PUT, preview) and only when email-service was
 * unreachable or answered 5xx. 401/404/409/4xx get the same answer on a retry. POST /emails
 * will never be retried here until it gets its own idempotency (Stage 4, spec 05-034).
 */
@Component
@ConditionalOnProperty("email-service.base-url")
public class EmailServiceGateway {

    static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_WAIT_MILLIS = 200;

    private final TemplatesApi templates;

    EmailServiceGateway(TemplatesApi templates) {
        this.templates = templates;
    }

    public Optional<EmailServiceTemplate> findTemplate(String name) {
        return retrying(() -> {
            try {
                EmailTemplate found = templates.getTemplate(name).getBody();
                return Optional.of(new EmailServiceTemplate(found.getName(), found.getSubject(), found.getBody()));
            } catch (FeignException.NotFound notFound) {
                return Optional.empty();
            } catch (FeignException e) {
                throw translate("GET /templates/" + name, e);
            }
        });
    }

    /**
     * Leaves email-service with exactly this template, whether or not it existed: PUT; on 404,
     * POST; if that POST hits 409 (another app instance created it meanwhile), PUT again. Running
     * it twice ends in the same state as running it once.
     */
    public void upsertTemplate(EmailServiceTemplate template) {
        if (update(template)) {
            return;
        }
        try {
            templates.createTemplate(new EmailTemplateCreateRequest()
                    .name(template.name()).subject(template.subject()).body(template.body()));
        } catch (FeignException.Conflict createdMeanwhile) {
            if (!update(template)) {
                throw new IllegalStateException("Template " + template.name()
                        + " answered 409 on create and 404 on update", createdMeanwhile);
            }
        } catch (FeignException e) {
            throw translate("POST /templates (" + template.name() + ")", e);
        }
    }

    public TemplatePreview preview(String name, Map<String, Object> variables) {
        return retrying(() -> {
            try {
                TemplatePreviewResponse rendered = templates
                        .previewTemplate(name, new TemplatePreviewRequest().variables(variables)).getBody();
                return new TemplatePreview(rendered.getSubject(), rendered.getBody());
            } catch (FeignException e) {
                throw translate("POST /templates/" + name + "/preview", e);
            }
        });
    }

    /** PUT is idempotent, so it is retried; false means the template doesn't exist yet. */
    private boolean update(EmailServiceTemplate template) {
        return retrying(() -> {
            try {
                templates.updateTemplate(template.name(), new EmailTemplateUpdateRequest()
                        .subject(template.subject()).body(template.body()));
                return true;
            } catch (FeignException.NotFound notFound) {
                return false;
            } catch (FeignException e) {
                throw translate("PUT /templates/" + template.name(), e);
            }
        });
    }

    private static <T> T retrying(Supplier<T> idempotentCall) {
        for (int attempt = 1; ; attempt++) {
            try {
                return idempotentCall.get();
            } catch (EmailServiceUnavailableException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                sleepBeforeRetry();
            }
        }
    }

    private static void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_WAIT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static RuntimeException translate(String call, FeignException e) {
        if (e instanceof RetryableException || e.status() < 0 || e.status() >= 500) {
            return new EmailServiceUnavailableException("email-service unavailable on " + call
                    + " (status " + e.status() + ")", e);
        }
        if (e.status() == 401) {
            return new EmailServiceAuthenticationException("email-service rejected app's X-API-Key on " + call
                    + " -- check EMAIL_SERVICE_API_KEY", e);
        }
        return new EmailServiceRejectedException("email-service answered " + e.status() + " on " + call
                + ": " + e.contentUTF8(), e);
    }
}
