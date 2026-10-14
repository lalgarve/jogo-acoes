package dev.leilaalgarve.jogoacoes.emailservice.template;

import dev.leilaalgarve.jogoacoes.emailservice.EmailServiceApplication;
import dev.leilaalgarve.jogoacoes.emailservice.api.model.EmailTemplateCreateRequest;
import dev.leilaalgarve.jogoacoes.emailservice.common.testsupport.TcpForwarder;
import dev.leilaalgarve.jogoacoes.emailservice.common.testsupport.TemplateCleanupHooks;
import dev.leilaalgarve.jogoacoes.emailservice.send.EmailSendRepository;
import io.restassured.RestAssured;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.CreateTemplateRequest;
import software.amazon.awssdk.services.ses.model.DeleteTemplateRequest;
import software.amazon.awssdk.services.ses.model.GetTemplateRequest;
import software.amazon.awssdk.services.ses.model.Template;
import software.amazon.awssdk.services.ses.model.TemplateDoesNotExistException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Spec 05-044: after startup, email-service recreates on SES every template that only its
 * database still has -- in the background, without holding up startup, retrying until SES
 * answers. Each case boots a second email-service against the same PostgreSQL and LocalStack,
 * the way a restart would.
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class SesTemplateReconcilerIntegrationTest {

    private static final String CLIENT = "client-a";
    private static final String BODY = "Hello {{name}}";
    private static final Duration WAIT = Duration.ofSeconds(30);

    @Autowired
    private TemplateService templateService;

    @Autowired
    private EmailTemplateRepository templateRepository;

    @Autowired
    private EmailSendRepository sendRepository;

    @Autowired
    private SesClient sesClient;

    private final List<ConfigurableApplicationContext> restarted = new ArrayList<>();

    @BeforeEach
    @AfterEach
    void cleanUp() {
        restarted.forEach(ConfigurableApplicationContext::close);
        restarted.clear();
        new TemplateCleanupHooks(sendRepository, templateRepository, sesClient).cleanUp();
    }

    @Test
    void recreatesATemplateSesLostWithTheDatabaseContent() {
        register("welcome");
        loseOnSes("welcome");

        restart();

        await().atMost(WAIT).untilAsserted(() -> {
            Template template = sesTemplate("welcome");
            assertThat(template.subjectPart()).isEqualTo("Subject of welcome");
            assertThat(template.htmlPart()).isEqualTo(BODY);
        });
    }

    @Test
    void recreatesMoreTemplatesThanOnePageOfListTemplates() {
        List<String> names = IntStream.rangeClosed(1, 11).mapToObj(i -> "template-" + i).toList();
        names.forEach(this::register);
        names.forEach(this::loseOnSes);

        restart();

        await().atMost(WAIT).untilAsserted(() -> names.forEach(name ->
                assertThat(sesTemplate(name).subjectPart()).isEqualTo("Subject of " + name)));
    }

    @Test
    void leavesATemplateThatOnlySesHasAndWarnsAboutIt(CapturedOutput output) {
        register("welcome");
        loseOnSes("welcome");
        sesClient.createTemplate(CreateTemplateRequest.builder()
                .template(Template.builder().templateName(CLIENT + "__orphan")
                        .subjectPart("Orphan").htmlPart("Orphan").build())
                .build());

        restart();

        await().atMost(WAIT).untilAsserted(() -> {
            assertThat(sesTemplate("welcome").subjectPart()).isEqualTo("Subject of welcome");
            assertThat(output.getOut()).contains("WARN").contains(CLIENT + "__orphan");
        });
        assertThat(sesTemplate("orphan").subjectPart()).isEqualTo("Orphan");
    }

    @Test
    void startsWithSesDownAndRecreatesOnceSesAnswers(CapturedOutput output) {
        register("welcome");
        loseOnSes("welcome");

        try (TcpForwarder ses = new TcpForwarder("localhost", 4566)) {
            ConfigurableApplicationContext context = restart(
                    "--spring.cloud.aws.ses.endpoint=http://localhost:" + ses.port());

            // Up and answering requests while SES refuses every connection.
            int port = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
            RestAssured.given().port(port).basePath("/api").when().get("/templates")
                    .then().statusCode(401);
            await().atMost(WAIT).untilAsserted(() ->
                    assertThat(output.getOut()).contains("SES template reconciliation failed"));
            assertThat(sesHas("welcome")).isFalse();

            ses.open();

            await().atMost(WAIT).untilAsserted(() ->
                    assertThat(sesTemplate("welcome").subjectPart()).isEqualTo("Subject of welcome"));
        }
    }

    private void register(String name) {
        templateService.create(CLIENT, new EmailTemplateCreateRequest()
                .name(name).subject("Subject of " + name).body(BODY));
    }

    private void loseOnSes(String name) {
        sesClient.deleteTemplate(DeleteTemplateRequest.builder().templateName(CLIENT + "__" + name).build());
    }

    private Template sesTemplate(String name) {
        return sesClient.getTemplate(GetTemplateRequest.builder().templateName(CLIENT + "__" + name).build())
                .template();
    }

    private boolean sesHas(String name) {
        try {
            sesTemplate(name);
            return true;
        } catch (TemplateDoesNotExistException e) {
            return false;
        }
    }

    private ConfigurableApplicationContext restart(String... extraArgs) {
        List<String> args = new ArrayList<>(List.of(
                "--server.port=0",
                "--email.ses.reconciliation.initial-delay=200ms",
                "--email.ses.reconciliation.max-delay=1s"));
        args.addAll(List.of(extraArgs));
        ConfigurableApplicationContext context = new SpringApplicationBuilder(EmailServiceApplication.class)
                .run(args.toArray(String[]::new));
        restarted.add(context);
        return context;
    }
}
