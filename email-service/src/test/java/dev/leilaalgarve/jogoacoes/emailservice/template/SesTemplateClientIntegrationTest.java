package dev.leilaalgarve.jogoacoes.emailservice.template;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.DeleteTemplateRequest;
import software.amazon.awssdk.services.ses.model.GetTemplateRequest;
import software.amazon.awssdk.services.ses.model.Template;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-044: {@link SesTemplateClient#createIfAbsent} never changes a template SES already
 * has -- what keeps a reconciliation round from undoing a PUT that ran after it read the row.
 * Against the real LocalStack.
 */
@SpringBootTest
class SesTemplateClientIntegrationTest {

    private static final String SES_NAME = "client-a__existing";

    @Autowired
    private SesTemplateClient sesTemplateClient;

    @Autowired
    private SesClient sesClient;

    @AfterEach
    void deleteTemplate() {
        sesClient.deleteTemplate(DeleteTemplateRequest.builder().templateName(SES_NAME).build());
    }

    @Test
    void createIfAbsentLeavesAnExistingTemplateAsItIs() {
        sesTemplateClient.putTemplate(SES_NAME, "Current subject", "Current body");

        sesTemplateClient.createIfAbsent(SES_NAME, "Stale subject", "Stale body");

        Template template = sesClient.getTemplate(GetTemplateRequest.builder().templateName(SES_NAME).build())
                .template();
        assertThat(template.subjectPart()).isEqualTo("Current subject");
        assertThat(template.htmlPart()).isEqualTo("Current body");
    }
}
