package io.deployo.jogoacoes.email.lambda;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the handler against the LocalStack SES container Dev Services starts
 * automatically for @QuarkusTest (docs/iteracao-4.md, decision 3) -- requires Docker, so
 * this only actually runs where Docker is available (the `docker` CI profile). Not run or
 * validated in this session for that reason; not verifiable in this agent's sandbox.
 */
@QuarkusTest
class EmailSendHandlerTest {

    @Inject
    EmailSendHandler handler;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void sendsAWellFormedMessageWithoutError() throws Exception {
        SQSEvent event = eventWith(new EmailMessage("1", UUID.randomUUID().toString(),
                "player@example.com", "Assunto de teste", "<p>Corpo de teste</p>"));

        assertThatCode(() -> handler.handleRequest(event, null)).doesNotThrowAnyException();
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
