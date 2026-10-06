package dev.leilaalgarve.jogoacoes.emailservice.common.testsupport;

import dev.leilaalgarve.jogoacoes.emailservice.send.EmailSendRepository;
import dev.leilaalgarve.jogoacoes.emailservice.template.EmailTemplateRepository;
import io.cucumber.java.After;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.DeleteTemplateRequest;
import software.amazon.awssdk.services.ses.model.ListTemplatesRequest;
import software.amazon.awssdk.services.ses.model.ListTemplatesResponse;

/**
 * Wipes every template from the database and from SES after each scenario. Needed because
 * Postgres and LocalStack are now persistent across test runs (docker-compose.yml), unlike the
 * H2-in-memory + Testcontainers setup this replaced, which already started clean every
 * {@code mvn test} invocation (see specs/05-028-testes-exigem-docker-real/plan.md). Safe to wipe
 * everything unconditionally: this database/LocalStack instance is used only by this suite.
 *
 * <p>Sends (spec 05-031) go first: {@code email_send} references the template it was built from.
 */
public class TemplateCleanupHooks {

    private final EmailSendRepository sendRepository;
    private final EmailTemplateRepository repository;
    private final SesClient sesClient;

    public TemplateCleanupHooks(EmailSendRepository sendRepository, EmailTemplateRepository repository,
                                SesClient sesClient) {
        this.sendRepository = sendRepository;
        this.repository = repository;
        this.sesClient = sesClient;
    }

    @After
    public void cleanUp() {
        sendRepository.deleteAll();
        repository.deleteAll();

        String nextToken = null;
        do {
            ListTemplatesResponse response = sesClient.listTemplates(
                    ListTemplatesRequest.builder().nextToken(nextToken).build());
            response.templatesMetadata().forEach(metadata ->
                    sesClient.deleteTemplate(DeleteTemplateRequest.builder()
                            .templateName(metadata.name())
                            .build()));
            nextToken = response.nextToken();
        } while (nextToken != null);
    }
}
