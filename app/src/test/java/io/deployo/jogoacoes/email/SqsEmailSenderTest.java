package io.deployo.jogoacoes.email;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.deployo.jogoacoes.domain.EmailTemplate;
import io.deployo.jogoacoes.domain.SentEmail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqsEmailSenderTest {

    private static final String QUEUE_NAME = "jogo-acoes-email-commands";

    @Mock
    private EmailContentRenderer renderer;

    @Mock
    private SqsTemplate sqsTemplate;

    @Mock
    private SentEmailRecorder sentEmailRecorder;

    @Test
    void publishesTheRenderedContentTaggedWithTheSentEmailIdAsCorrelationId() {
        SqsEmailSender sender = new SqsEmailSender(renderer, sqsTemplate, sentEmailRecorder, QUEUE_NAME);

        EmailRequest request = new EmailRequest(1L, "alice@example.com", "Alice", null, null,
                "https://jogo-acoes.example/login-links/abc", EmailTemplate.LOGIN_LINK);
        RenderedEmail rendered = new RenderedEmail("Seu link de acesso", "<html>corpo renderizado</html>");
        SentEmail sentEmail = new SentEmail();
        sentEmail.setId(42L);

        when(renderer.render(request)).thenReturn(rendered);
        when(sentEmailRecorder.record(request)).thenReturn(sentEmail);

        sender.send(request);

        verify(sqsTemplate).send(eq(QUEUE_NAME), eq(new EmailMessage("1", "42", "alice@example.com",
                "Seu link de acesso", "<html>corpo renderizado</html>")));
    }
}
