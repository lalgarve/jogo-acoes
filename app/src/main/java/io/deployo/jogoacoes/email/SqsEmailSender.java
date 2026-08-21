package io.deployo.jogoacoes.email;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.deployo.jogoacoes.domain.SentEmail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Publishes the fully-rendered e-mail to the SQS command queue instead of sending anything
 * itself (docs/context/iteracao-4.md, decisão 1) -- the email-lambda module consumes the queue and
 * calls SES. {@code sent_email} is still recorded here, synchronously, at publish time
 * (decisão 9): its generated id doubles as the message's {@code correlationId}, so no separate
 * column/UUID is needed to tie a queue message back to its row.
 *
 * <p>Active wherever {@code email.sender: sqs} is set -- {@code staging}/{@code production}
 * (real AWS, see the respective {@code application-*.yml}) and {@code docker}/CI, where
 * {@link SqsTemplate} resolves against the LocalStack service docker-compose.yml provides.
 */
@Service
@ConditionalOnProperty(name = "email.sender", havingValue = "sqs")
public class SqsEmailSender implements EmailSender {

    private final EmailContentRenderer renderer;
    private final SqsTemplate sqsTemplate;
    private final SentEmailRecorder sentEmailRecorder;
    private final String queueName;

    public SqsEmailSender(EmailContentRenderer renderer, SqsTemplate sqsTemplate, SentEmailRecorder sentEmailRecorder,
                           @Value("${email.queue-name}") String queueName) {
        this.renderer = renderer;
        this.sqsTemplate = sqsTemplate;
        this.sentEmailRecorder = sentEmailRecorder;
        this.queueName = queueName;
    }

    @Override
    public void send(EmailRequest request) {
        RenderedEmail rendered = renderer.render(request);
        SentEmail sentEmail = sentEmailRecorder.record(request);

        EmailMessage message = new EmailMessage("1", sentEmail.getId().toString(), request.email(),
                rendered.subject(), rendered.body());
        sqsTemplate.send(queueName, message);
    }
}
