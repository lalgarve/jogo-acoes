package dev.leilaalgarve.jogoacoes.emailservice.template;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.AlreadyExistsException;
import software.amazon.awssdk.services.ses.model.CreateTemplateRequest;
import software.amazon.awssdk.services.ses.model.InvalidRenderingParameterException;
import software.amazon.awssdk.services.ses.model.InvalidTemplateException;
import software.amazon.awssdk.services.ses.model.LimitExceededException;
import software.amazon.awssdk.services.ses.model.MissingRenderingAttributeException;
import software.amazon.awssdk.services.ses.model.SesException;
import software.amazon.awssdk.services.ses.model.Template;
import software.amazon.awssdk.services.ses.model.TemplateDoesNotExistException;
import software.amazon.awssdk.services.ses.model.TestRenderTemplateRequest;
import software.amazon.awssdk.services.ses.model.UpdateTemplateRequest;

/**
 * Encapsulates the SES template operations (plan.md): the namespaced name
 * (<code>&lt;clientId&gt;__&lt;name&gt;</code>) is an internal detail that never leaks past this
 * class -- callers only ever see {@link TemplateRejectedBySesException}/
 * {@link TemplateRenderFailedException}, not raw SES exception types.
 */
@Component
public class SesTemplateClient {

    private final SesClient sesClient;

    public SesTemplateClient(SesClient sesClient) {
        this.sesClient = sesClient;
    }

    public String namespacedName(String clientId, String name) {
        return clientId + "__" + name;
    }

    public void createTemplate(String sesTemplateName, String subject, String body) {
        Template template = Template.builder()
                .templateName(sesTemplateName)
                .subjectPart(subject)
                .htmlPart(body)
                .build();
        try {
            sesClient.createTemplate(CreateTemplateRequest.builder().template(template).build());
        } catch (InvalidTemplateException | AlreadyExistsException | LimitExceededException e) {
            throw new TemplateRejectedBySesException(e.getMessage());
        } catch (SesException e) {
            throw new TemplateRejectedBySesException(e.getMessage());
        }
    }

    public void updateTemplate(String sesTemplateName, String subject, String body) {
        Template template = Template.builder()
                .templateName(sesTemplateName)
                .subjectPart(subject)
                .htmlPart(body)
                .build();
        try {
            sesClient.updateTemplate(UpdateTemplateRequest.builder().template(template).build());
        } catch (InvalidTemplateException e) {
            throw new TemplateRejectedBySesException(e.getMessage());
        } catch (TemplateDoesNotExistException e) {
            // Defensive fallback: our own DB check already guarantees the template exists
            // before this is called, so this would mean the SES-side template was deleted
            // out-of-band.
            throw new TemplateNotFoundException(sesTemplateName);
        } catch (SesException e) {
            throw new TemplateRejectedBySesException(e.getMessage());
        }
    }

    public String testRenderTemplate(String sesTemplateName, String templateDataJson) {
        TestRenderTemplateRequest request = TestRenderTemplateRequest.builder()
                .templateName(sesTemplateName)
                .templateData(templateDataJson)
                .build();
        try {
            return sesClient.testRenderTemplate(request).renderedTemplate();
        } catch (TemplateDoesNotExistException e) {
            throw new TemplateNotFoundException(sesTemplateName);
        } catch (InvalidRenderingParameterException | MissingRenderingAttributeException e) {
            throw new TemplateRenderFailedException(e.getMessage());
        } catch (SesException e) {
            throw new TemplateRenderFailedException(e.getMessage());
        }
    }
}
