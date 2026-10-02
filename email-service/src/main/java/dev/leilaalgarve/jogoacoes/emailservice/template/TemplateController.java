package dev.leilaalgarve.jogoacoes.emailservice.template;

import dev.leilaalgarve.jogoacoes.emailservice.api.TemplatesApi;
import dev.leilaalgarve.jogoacoes.emailservice.api.model.EmailTemplateCreateRequest;
import dev.leilaalgarve.jogoacoes.emailservice.api.model.EmailTemplateUpdateRequest;
import dev.leilaalgarve.jogoacoes.emailservice.api.model.TemplatePreviewRequest;
import dev.leilaalgarve.jogoacoes.emailservice.api.model.TemplatePreviewResponse;
import dev.leilaalgarve.jogoacoes.emailservice.auth.ClientIdentityResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class TemplateController implements TemplatesApi {

    private final ClientIdentityResolver clientIdentityResolver;
    private final TemplateService templateService;

    public TemplateController(ClientIdentityResolver clientIdentityResolver, TemplateService templateService) {
        this.clientIdentityResolver = clientIdentityResolver;
        this.templateService = templateService;
    }

    @Override
    public ResponseEntity<List<dev.leilaalgarve.jogoacoes.emailservice.api.model.EmailTemplate>> listTemplates() {
        return ResponseEntity.ok(templateService.list(clientIdentityResolver.currentClientId()));
    }

    @Override
    public ResponseEntity<dev.leilaalgarve.jogoacoes.emailservice.api.model.EmailTemplate> getTemplate(String name) {
        return ResponseEntity.ok(templateService.get(clientIdentityResolver.currentClientId(), name));
    }

    @Override
    public ResponseEntity<dev.leilaalgarve.jogoacoes.emailservice.api.model.EmailTemplate> createTemplate(
            EmailTemplateCreateRequest emailTemplateCreateRequest) {
        dev.leilaalgarve.jogoacoes.emailservice.api.model.EmailTemplate created =
                templateService.create(clientIdentityResolver.currentClientId(), emailTemplateCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @Override
    public ResponseEntity<dev.leilaalgarve.jogoacoes.emailservice.api.model.EmailTemplate> updateTemplate(
            String name, EmailTemplateUpdateRequest emailTemplateUpdateRequest) {
        return ResponseEntity.ok(
                templateService.update(clientIdentityResolver.currentClientId(), name, emailTemplateUpdateRequest));
    }

    @Override
    public ResponseEntity<TemplatePreviewResponse> previewTemplate(String name,
                                                                     TemplatePreviewRequest templatePreviewRequest) {
        return ResponseEntity.ok(
                templateService.preview(clientIdentityResolver.currentClientId(), name, templatePreviewRequest));
    }
}
