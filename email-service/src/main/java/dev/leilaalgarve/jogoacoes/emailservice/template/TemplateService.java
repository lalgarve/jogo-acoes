package dev.leilaalgarve.jogoacoes.emailservice.template;

import dev.leilaalgarve.jogoacoes.emailservice.api.model.EmailTemplateCreateRequest;
import dev.leilaalgarve.jogoacoes.emailservice.api.model.EmailTemplateUpdateRequest;
import dev.leilaalgarve.jogoacoes.emailservice.api.model.TemplatePreviewRequest;
import dev.leilaalgarve.jogoacoes.emailservice.api.model.TemplatePreviewResponse;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Orchestrates template registration: SES is always called before the database is touched
 * (data-model.md, "Invariantes") -- a row only exists here once SES has confirmed the template
 * is valid.
 */
@Service
public class TemplateService {

    private final EmailTemplateRepository repository;
    private final SesTemplateClient sesTemplateClient;
    private final MimeRenderedTemplateParser mimeParser;
    private final ObjectMapper objectMapper;

    public TemplateService(EmailTemplateRepository repository, SesTemplateClient sesTemplateClient,
                            MimeRenderedTemplateParser mimeParser, ObjectMapper objectMapper) {
        this.repository = repository;
        this.sesTemplateClient = sesTemplateClient;
        this.mimeParser = mimeParser;
        this.objectMapper = objectMapper;
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
        sesTemplateClient.createTemplate(sesTemplateName, request.getSubject(), request.getBody());

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
        String rawMime = sesTemplateClient.testRenderTemplate(entity.getSesTemplateName(), templateDataJson);
        MimeRenderedTemplateParser.RenderedTemplate rendered = mimeParser.parse(rawMime);
        return new TemplatePreviewResponse().subject(rendered.subject()).body(rendered.body());
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
