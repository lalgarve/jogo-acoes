package dev.leilaalgarve.jogoacoes.emailservice.template;

import dev.leilaalgarve.jogoacoes.emailservice.api.model.EmailTemplateCreateRequest;
import dev.leilaalgarve.jogoacoes.emailservice.api.model.EmailTemplateUpdateRequest;
import dev.leilaalgarve.jogoacoes.emailservice.api.model.TemplatePreviewRequest;
import dev.leilaalgarve.jogoacoes.emailservice.api.model.TemplatePreviewResponse;
import org.openapitools.jackson.nullable.JsonNullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Orchestrates template registration: SES is always called before the database is touched
 * (data-model.md, "Invariantes") -- a row only exists here once SES has confirmed the template
 * is valid. The database is the source of truth: whatever SES lost is recreated from the row
 * (spec 05-044).
 */
@Service
public class TemplateService {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);

    private final EmailTemplateRepository repository;
    private final SesTemplateClient sesTemplateClient;
    private final MimeRenderedTemplateParser mimeParser;
    private final ObjectMapper objectMapper;
    private final Duration recreateWindow;

    public TemplateService(EmailTemplateRepository repository, SesTemplateClient sesTemplateClient,
                            MimeRenderedTemplateParser mimeParser, ObjectMapper objectMapper,
                            @Value("${email.template.recreate-window:1h}") Duration recreateWindow) {
        this.repository = repository;
        this.sesTemplateClient = sesTemplateClient;
        this.mimeParser = mimeParser;
        this.objectMapper = objectMapper;
        this.recreateWindow = recreateWindow;
    }

    public List<dev.leilaalgarve.jogoacoes.emailservice.api.model.EmailTemplate> list(String clientId) {
        return repository.findAllByClientId(clientId).stream().map(this::toApiModel).toList();
    }

    public dev.leilaalgarve.jogoacoes.emailservice.api.model.EmailTemplate get(String clientId, String name) {
        return toApiModel(findOrThrow(clientId, name));
    }

    public dev.leilaalgarve.jogoacoes.emailservice.api.model.EmailTemplate create(String clientId,
                                                                                   EmailTemplateCreateRequest request) {
        if (repository.existsByClientIdAndName(clientId, request.getName())) {
            throw new TemplateAlreadyExistsException(request.getName());
        }
        String sesTemplateName = sesTemplateClient.namespacedName(clientId, request.getName());
        // Overwrites a template SES still has under this name with no row here (spec 05-044):
        // the <client>__ prefix belongs to this service.
        sesTemplateClient.putTemplate(sesTemplateName, request.getSubject(), request.getBody());

        EmailTemplate entity = new EmailTemplate(clientId, request.getName(), request.getSubject(),
                request.getBody(), toJson(request.getVariablesSchema()), sesTemplateName, Instant.now());
        return toApiModel(repository.save(entity));
    }

    public dev.leilaalgarve.jogoacoes.emailservice.api.model.EmailTemplate update(String clientId, String name,
                                                                                   EmailTemplateUpdateRequest request) {
        EmailTemplate entity = findOrThrow(clientId, name);
        sesTemplateClient.updateTemplate(entity.getSesTemplateName(), request.getSubject(), request.getBody());
        entity.update(request.getSubject(), request.getBody(), toJson(request.getVariablesSchema()), Instant.now());
        return toApiModel(repository.save(entity));
    }

    public TemplatePreviewResponse preview(String clientId, String name, TemplatePreviewRequest request) {
        EmailTemplate entity = findOrThrow(clientId, name);
        String templateDataJson = toJsonRequired(request.getVariables());
        String rawMime;
        try {
            rawMime = sesTemplateClient.testRenderTemplate(entity.getSesTemplateName(), templateDataJson);
        } catch (TemplateNotFoundException e) {
            // The row exists, so SES lost it (spec 05-044): recreate it from the row, render once more.
            sesTemplateClient.createIfAbsent(entity.getSesTemplateName(), entity.getSubject(), entity.getBody());
            rawMime = sesTemplateClient.testRenderTemplate(entity.getSesTemplateName(), templateDataJson);
        }
        MimeRenderedTemplateParser.RenderedTemplate rendered = mimeParser.parse(rawMime);
        return new TemplatePreviewResponse().subject(rendered.subject()).body(rendered.body());
    }

    /** The client's own template named {@code name}, to send an e-mail from (spec 05-031). */
    public TemplateReference reference(String clientId, String name) {
        EmailTemplate entity = findOrThrow(clientId, name);
        return new TemplateReference(entity.getId(), entity.getSesTemplateName());
    }

    /**
     * Spec 05-046: a send failed because SES does not have the template -- recreate it from the
     * row, unless this service already did within the recreate window (the rest of a burst of
     * errors for the same template). The row lock makes concurrent errors wait for the first one
     * instead of each calling SES. A template deleted since then is left alone.
     *
     * @throws SesUnavailableException SES did not answer; nothing is recorded, the next error tries again
     */
    @Transactional
    public void recreateOnSesUnlessRecent(Long templateId) {
        Instant now = Instant.now();
        repository.lockById(templateId)
                .filter(template -> !template.recreatedOnSesAfter(now.minus(recreateWindow)))
                .ifPresent(template -> {
                    sesTemplateClient.createIfAbsent(template.getSesTemplateName(), template.getSubject(),
                            template.getBody());
                    template.markRecreatedOnSes(now);
                    log.info("Recreated template {} on SES after a TEMPLATE_MISSING delivery error",
                            template.getSesTemplateName());
                });
    }

    private EmailTemplate findOrThrow(String clientId, String name) {
        return repository.findByClientIdAndName(clientId, name).orElseThrow(() -> new TemplateNotFoundException(name));
    }

    private dev.leilaalgarve.jogoacoes.emailservice.api.model.EmailTemplate toApiModel(EmailTemplate entity) {
        dev.leilaalgarve.jogoacoes.emailservice.api.model.EmailTemplate api =
                new dev.leilaalgarve.jogoacoes.emailservice.api.model.EmailTemplate()
                        .name(entity.getName())
                        .subject(entity.getSubject())
                        .body(entity.getBody())
                        .createdAt(toOffsetDateTime(entity.getCreatedAt()))
                        .updatedAt(toOffsetDateTime(entity.getUpdatedAt()));
        if (entity.getVariablesSchema() != null) {
            api.variablesSchema(fromJson(entity.getVariablesSchema()));
        }
        return api;
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private String toJson(JsonNullable<Object> nullable) {
        if (nullable == null || !nullable.isPresent() || nullable.get() == null) {
            return null;
        }
        return toJsonRequired(nullable.get());
    }

    private String toJsonRequired(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private Object fromJson(String json) {
        return objectMapper.readValue(json, Object.class);
    }
}
