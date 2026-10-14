package dev.leilaalgarve.jogoacoes.emailservice.domain.steps;

import dev.leilaalgarve.jogoacoes.emailservice.common.testsupport.EmailServiceScenarioWorld;
import dev.leilaalgarve.jogoacoes.emailservice.domain.ApprovedDomain;
import dev.leilaalgarve.jogoacoes.emailservice.domain.ApprovedDomainRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-047: the whitelist read back from the real database after each send.
 * {@code ghost.test} is not in the test DNS zone, so it can only be accepted by the whitelist.
 */
public class RecipientDomainSteps {

    private final EmailServiceScenarioWorld world;
    private final ApprovedDomainRepository approvedDomains;
    private final JdbcTemplate jdbcTemplate;

    public RecipientDomainSteps(EmailServiceScenarioWorld world, ApprovedDomainRepository approvedDomains,
                                JdbcTemplate jdbcTemplate) {
        this.world = world;
        this.approvedDomains = approvedDomains;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Given("the domain {string} is already whitelisted")
    public void the_domain_is_already_whitelisted(String domain) {
        approvedDomains.approve(domain, "MX", Instant.now());
    }

    @Then("the domain {string} is whitelisted as approved by {string}")
    public void the_domain_is_whitelisted_as_approved_by(String domain, String record) {
        ApprovedDomain approved = approvedDomains.findById(domain).orElseThrow(
                () -> new AssertionError(domain + " is not whitelisted"));
        assertThat(approved.getApprovedBy()).isEqualTo(record);
    }

    @Then("the domain {string} is not whitelisted")
    public void the_domain_is_not_whitelisted(String domain) {
        assertThat(approvedDomains.existsById(domain)).isFalse();
    }

    /**
     * Spec 05-048: written straight to the table, as an import of a repository of the suite's
     * own would leave it (DisposableDomainCleanupHooks deletes it afterwards).
     */
    @Given("the domain {string} is on an imported disposable domain list")
    public void the_domain_is_on_an_imported_disposable_domain_list(String domain) {
        Long sourceId = jdbcTemplate.queryForObject(
                "INSERT INTO email_service.disposable_domain_source (url, description, created_at)"
                        + " VALUES (?, 'Send scenarios', now()) RETURNING id",
                Long.class, "http://lists.test/" + UUID.randomUUID() + ".conf");
        jdbcTemplate.update(
                "INSERT INTO email_service.disposable_domain (source_id, domain, last_seen_execution_id) VALUES (?, ?, 0)",
                sourceId, domain);
    }

    @Then("the system rejects the send because the domain {string} is a disposable e-mail domain")
    public void the_system_rejects_the_send_because_the_domain_is_a_disposable_email_domain(String domain) {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(422);
        assertThat(world.getLastResponse().jsonPath().getString("message"))
                .isEqualTo("Recipient domain is a disposable e-mail domain: " + domain);
    }

    @Then("the system rejects the send because the domain {string} does not accept e-mail")
    public void the_system_rejects_the_send_because_the_domain_does_not_accept_email(String domain) {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(422);
        assertThat(world.getLastResponse().jsonPath().getString("message"))
                .isEqualTo("Recipient domain does not accept e-mail: " + domain);
    }
}
