package io.deployo.jogoacoes.email.lambda;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.VerifyEmailIdentityRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Exercises the handler against the LocalStack SES container Dev Services starts
 * automatically for @QuarkusTest (docs/iteracao-4.md, decision 3) -- requires Docker.
 * sendsAWellFormedMessageWithoutError degrades to SKIPPED (not failed) without Docker, the
 * same way app's tests fully pass in the H2-backed sandbox profile rather than needing
 * Postgres -- there's no H2-equivalent fake for SES, so "skip gracefully" is this module's
 * version of that. Confirmed by actually running this in the sandbox this session: without
 * Docker, this test is reported skipped and rejectsAMalformedMessage (which never reaches
 * the SES client) still passes, so `mvn test` exits 0 -- it does not just fail outright.
 *
 * <p>Real finding, from this test's first-ever run against actual Docker (GitHub Actions,
 * not this sandbox): LocalStack's SES emulation enforces the same "sender must be a verified
 * identity" rule real SES sandbox mode does -- application.properties's original comment
 * assumed otherwise, untested. {@code verifyEmailIdentity} fixes it: LocalStack marks an
 * identity verified immediately on that call, no confirmation e-mail round trip like real SES
 * (unverified, reasoned from LocalStack's documented SES emulation behavior -- the actual
 * proof is the next green run of this test in CI).
 */
@QuarkusTest
class EmailSendHandlerTest {

    @Inject
    EmailSendHandler handler;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    SesClient sesClient;

    @ConfigProperty(name = "email.sender-address")
    String senderAddress;

    @Test
    void sendsAWellFormedMessageWithoutError() throws Exception {
        SQSEvent event = eventWith(new EmailMessage("1", UUID.randomUUID().toString(),
                "player@example.com", "Assunto de teste", "<p>Corpo de teste</p>"));

        try {
            sesClient.verifyEmailIdentity(VerifyEmailIdentityRequest.builder().emailAddress(senderAddress).build());
            handler.handleRequest(event, null);
        } catch (RuntimeException e) {
            SdkClientException noRegion = findCause(e, SdkClientException.class);
            if (noRegion == null) {
                throw e;
            }
            assumeTrue(false, "No AWS region/endpoint resolved -- Dev Services couldn't start "
                    + "LocalStack (no Docker in this environment). Skipping, not failing: " + noRegion.getMessage());
        }
    }

    private static <T extends Throwable> T findCause(Throwable root, Class<T> type) {
        for (Throwable t = root; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return type.cast(t);
            }
        }
        return null;
    }

    @Test
    void rejectsAMalformedMessage() {
        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setBody("not-json");
        SQSEvent event = new SQSEvent();
        event.setRecords(List.of(message));

        assertThatThrownBy(() -> handler.handleRequest(event, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SQSEvent eventWith(EmailMessage payload) throws Exception {
        SQSEvent.SQSMessage message = new SQSEvent.SQSMessage();
        message.setBody(objectMapper.writeValueAsString(payload));
        SQSEvent event = new SQSEvent();
        event.setRecords(List.of(message));
        return event;
    }
}
