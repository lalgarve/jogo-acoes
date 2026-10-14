package dev.leilaalgarve.jogoacoes.emailservice.domain.steps;

import dev.leilaalgarve.jogoacoes.emailservice.common.testsupport.EmailServiceScenarioWorld;
import dev.leilaalgarve.jogoacoes.emailservice.domain.ApprovedDomain;
import dev.leilaalgarve.jogoacoes.emailservice.domain.ApprovedDomainRepository;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec 05-047: the whitelist read back from the real database after each send.
 * {@code ghost.test} is not in the test DNS zone, so it can only be accepted by the whitelist.
 */
public class RecipientDomainSteps {

    private final EmailServiceScenarioWorld world;
    private final ApprovedDomainRepository approvedDomains;

    public RecipientDomainSteps(EmailServiceScenarioWorld world, ApprovedDomainRepository approvedDomains) {
        this.world = world;
        this.approvedDomains = approvedDomains;
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

    @Then("the system rejects the send because the domain {string} does not accept e-mail")
    public void the_system_rejects_the_send_because_the_domain_does_not_accept_email(String domain) {
        assertThat(world.getLastResponse().statusCode()).isEqualTo(422);
        assertThat(world.getLastResponse().jsonPath().getString("message"))
                .isEqualTo("Recipient domain does not accept e-mail: " + domain);
    }
}
