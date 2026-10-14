package dev.leilaalgarve.jogoacoes.emailservice.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec 05-047: the verifier against the real PostgreSQL whitelist and the CoreDNS test zone --
 * the cases the HTTP scenarios cannot reach (the request DTO's {@code format: email} rejects them
 * first) and concurrent approvals of the same new domain.
 */
@SpringBootTest
class RecipientDomainVerifierTest {

    @Autowired
    private RecipientDomainVerifier verifier;

    @Autowired
    private ApprovedDomainRepository approvedDomains;

    @BeforeEach
    @AfterEach
    void keepOnlyTheSeededDomains() {
        approvedDomains.deleteAllNotSeeded();
    }

    @Test
    void tenSimultaneousSendsToTheSameNewDomainApproveItOnce() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            List<Callable<Void>> sends = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                int n = i;
                sends.add(() -> {
                    verifier.verify("player" + n + "@with-mx.test");
                    return null;
                });
            }
            for (Future<Void> send : executor.invokeAll(sends)) {
                send.get();
            }
        } finally {
            executor.shutdown();
        }

        assertThat(approvedDomains.findAll())
                .filteredOn(domain -> domain.getDomain().equals("with-mx.test"))
                .singleElement()
                .satisfies(domain -> assertThat(domain.getApprovedBy()).isEqualTo("MX"));
    }

    @Test
    void anInternationalizedDomainIsCheckedInItsAsciiForm() {
        // Not in the test zone: rejected as missing, under its ASCII (punycode) name.
        assertThatThrownBy(() -> verifier.verify("jogador@ação.test"))
                .isInstanceOf(RecipientDomainRejectedException.class)
                .hasMessage("Recipient domain does not accept e-mail: xn--ao-siap.test");
    }

    @Test
    void aDomainIdnCannotConvertIsRejected() {
        String tooLongLabel = "a".repeat(64);

        assertThatThrownBy(() -> verifier.verify("player@" + tooLongLabel + ".test"))
                .isInstanceOf(RecipientDomainRejectedException.class)
                .hasMessage("Recipient domain does not accept e-mail: " + tooLongLabel + ".test");
        assertThat(approvedDomains.existsById(tooLongLabel + ".test")).isFalse();
    }
}
