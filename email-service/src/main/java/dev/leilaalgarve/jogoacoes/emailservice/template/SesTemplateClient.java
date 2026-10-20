package dev.leilaalgarve.jogoacoes.emailservice.template;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.AlreadyExistsException;
import software.amazon.awssdk.services.ses.model.CreateTemplateRequest;
import software.amazon.awssdk.services.ses.model.InvalidRenderingParameterException;
import software.amazon.awssdk.services.ses.model.ListTemplatesRequest;
import software.amazon.awssdk.services.ses.model.ListTemplatesResponse;
import software.amazon.awssdk.services.ses.model.MissingRenderingAttributeException;
import software.amazon.awssdk.services.ses.model.SesException;
import software.amazon.awssdk.services.ses.model.Template;
import software.amazon.awssdk.services.ses.model.TemplateDoesNotExistException;
import software.amazon.awssdk.services.ses.model.TemplateMetadata;
import software.amazon.awssdk.services.ses.model.TestRenderTemplateRequest;
import software.amazon.awssdk.services.ses.model.UpdateTemplateRequest;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

/**
 * Encapsulates the SES template operations (plan.md): the namespaced name
 * (<code>&lt;clientId&gt;__&lt;name&gt;</code>) is an internal detail that never leaks past this
 * class -- callers only ever see {@link TemplateRejectedBySesException}/
 * {@link TemplateRenderFailedException}/{@link SesUnavailableException}, not raw SES exception
 * types.
 *
 * <p>SES v1 has no "upsert" (spec 05-044): the database is the source of truth, so every write
 * here leaves SES with the content asked for, whether or not SES still had the template.
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

    /** Creates the template, overwriting one SES already has under this name (spec 05-044). */
    public void putTemplate(String sesTemplateName, String subject, String body) {
        Template template = template(sesTemplateName, subject, body);
        call(() -> {
            try {
                sesClient.createTemplate(CreateTemplateRequest.builder().template(template).build());
            } catch (AlreadyExistsException e) {
                sesClient.updateTemplate(UpdateTemplateRequest.builder().template(template).build());
            }
            return null;
        }, TemplateRejectedBySesException::new);
    }

    /**
     * Creates the template only if SES does not have it; never changes one that exists (spec
     * 05-044, the reconciliation: a PUT may have updated it after the database row was read).
     */
    public void createIfAbsent(String sesTemplateName, String subject, String body) {
        Template template = template(sesTemplateName, subject, body);
        call(() -> {
            try {
                sesClient.createTemplate(CreateTemplateRequest.builder().template(template).build());
            } catch (AlreadyExistsException e) {
                // already there: nothing to recreate
            }
            return null;
        }, TemplateRejectedBySesException::new);
    }

    /** Updates the template, recreating it if SES lost it (spec 05-044). */
    public void updateTemplate(String sesTemplateName, String subject, String body) {
        Template template = template(sesTemplateName, subject, body);
        call(() -> {
            try {
                sesClient.updateTemplate(UpdateTemplateRequest.builder().template(template).build());
            } catch (TemplateDoesNotExistException e) {
                sesClient.createTemplate(CreateTemplateRequest.builder().template(template).build());
            }
            return null;
        }, TemplateRejectedBySesException::new);
    }

    /** Every template name SES has, across all pages of ListTemplates. */
    public Set<String> listTemplateNames() {
        return call(() -> {
            Set<String> names = new HashSet<>();
            String nextToken = null;
            do {
                ListTemplatesResponse response = sesClient.listTemplates(
                        ListTemplatesRequest.builder().nextToken(nextToken).build());
                response.templatesMetadata().stream().map(TemplateMetadata::name).forEach(names::add);
                nextToken = response.nextToken();
            } while (nextToken != null);
            return names;
        }, TemplateRejectedBySesException::new);
    }

    public String testRenderTemplate(String sesTemplateName, String templateDataJson) {
        TestRenderTemplateRequest request = TestRenderTemplateRequest.builder()
                .templateName(sesTemplateName)
                .templateData(templateDataJson)
                .build();
        return call(() -> {
            try {
                return sesClient.testRenderTemplate(request).renderedTemplate();
            } catch (TemplateDoesNotExistException e) {
                throw new TemplateNotFoundException(sesTemplateName);
            } catch (InvalidRenderingParameterException | MissingRenderingAttributeException e) {
                throw new TemplateRenderFailedException(e.getMessage());
            }
        }, TemplateRenderFailedException::new);
    }

    private static Template template(String sesTemplateName, String subject, String body) {
        return Template.builder()
                .templateName(sesTemplateName)
                .subjectPart(subject)
                .htmlPart(body)
                .build();
    }

    /**
     * Runs one SES call and translates what the SDK throws: SES unreachable, 5xx or throttling →
     * {@link SesUnavailableException}; any other SES answer → {@code rejected}, with SES's reason.
     */
    private static <T> T call(SesCall<T> call, Function<String, RuntimeException> rejected) {
        try {
            return call.run();
        } catch (SdkClientException e) {
            throw new SesUnavailableException("SES could not be reached: " + e.getMessage(), e);
        } catch (SesException e) {
            if (e.statusCode() >= 500 || e.isThrottlingException()) {
                throw new SesUnavailableException("SES is unavailable: " + e.getMessage(), e);
            }
            throw rejected.apply(e.getMessage());
        } catch (SdkException e) {
            throw new SesUnavailableException("SES call failed: " + e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface SesCall<T> {
        T run();
    }
}
