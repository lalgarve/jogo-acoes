package dev.leilaalgarve.jogoacoes.emailservice.common.testsupport;

import dev.leilaalgarve.jogoacoes.emailservice.domain.ApprovedDomainRepository;
import io.cucumber.java.After;
import io.cucumber.java.Before;

/**
 * Spec 05-047: every scenario starts with the whitelist as the migration left it -- only the SEED
 * rows -- so a domain approved by an earlier scenario (or run) is looked up again.
 */
public class ApprovedDomainCleanupHooks {

    private final ApprovedDomainRepository approvedDomains;

    public ApprovedDomainCleanupHooks(ApprovedDomainRepository approvedDomains) {
        this.approvedDomains = approvedDomains;
    }

    @Before
    @After
    public void keepOnlyTheSeededDomains() {
        approvedDomains.deleteAllNotSeeded();
    }
}
