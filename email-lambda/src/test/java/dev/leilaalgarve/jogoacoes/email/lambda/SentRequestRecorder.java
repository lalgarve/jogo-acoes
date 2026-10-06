package dev.leilaalgarve.jogoacoes.email.lambda;

import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.services.ses.model.SendTemplatedEmailRequest;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records every {@code SendTemplatedEmail} request the real SES client sends (registered through
 * {@code quarkus.ses.interceptors}, src/test/resources/application.properties) -- the request still
 * goes to LocalStack unchanged. Needed only for the message tags: LocalStack's own SES store
 * ({@code GET /_aws/ses}) keeps the template, its data, sender and recipient, but not the tags.
 * Instantiated by the SDK, not by CDI, hence the static list.
 */
public class SentRequestRecorder implements ExecutionInterceptor {

    private static final List<SendTemplatedEmailRequest> SENT = new CopyOnWriteArrayList<>();

    @Override
    public void beforeExecution(Context.BeforeExecution context, ExecutionAttributes executionAttributes) {
        if (context.request() instanceof SendTemplatedEmailRequest request) {
            SENT.add(request);
        }
    }

    static Optional<SendTemplatedEmailRequest> sentFrom(String source) {
        return SENT.stream().filter(request -> source.equals(request.source())).findFirst();
    }
}
