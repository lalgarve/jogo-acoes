package dev.leilaalgarve.jogoacoes.emailservice;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Real LocalStack (SES module only), not a mocked {@code SesClient} -- the SES call is the
 * behavior under test here (plan.md, "SES real ou mock"), unlike {@code app}'s stubbed e-mail
 * sender. The endpoint/credentials come from src/test/resources/application.yml, pointing at
 * docker-compose.yml's own {@code localstack} service -- this class no longer starts a container
 * itself (see specs/05-028-testes-exigem-docker-real/plan.md).
 *
 * <p>PostgreSQL and LocalStack must already be running from docker-compose.yml before this
 * suite starts. The two scenarios that require real Amazon SES rather than LocalStack are tagged
 * {@code @requires-real-ses} and excluded by the Cucumber suite runner until they can be verified
 * against SES (Iteration 6 issue #104).
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CucumberSpringConfiguration {
}
