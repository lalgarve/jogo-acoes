package dev.leilaalgarve.jogoacoes.emailservice;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Real LocalStack (SES module only), not a mocked {@code SesClient} -- the SES call is the
 * behavior under test here (plan.md, "SES real ou mock"), unlike {@code app}'s stubbed e-mail
 * sender. Same base image tag as docker-compose.yml's own {@code localstack} service.
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CucumberSpringConfiguration {

    private static final LocalStackContainer LOCALSTACK =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:4"))
                    .withServices(LocalStackContainer.Service.SES);

    static {
        LOCALSTACK.start();
    }

    @DynamicPropertySource
    static void localstackProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.aws.ses.endpoint",
                () -> LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.SES).toString());
        registry.add("spring.cloud.aws.credentials.access-key", LOCALSTACK::getAccessKey);
        registry.add("spring.cloud.aws.credentials.secret-key", LOCALSTACK::getSecretKey);
        registry.add("spring.cloud.aws.region.static", LOCALSTACK::getRegion);
    }
}
