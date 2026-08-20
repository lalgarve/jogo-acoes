package io.deployo.jogoacoes.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Doesn't actually send anything -- records the send in {@code sent_email} instead. The
 * default {@link EmailSender} (matchIfMissing): active in {@code sandbox} and in tests, neither
 * of which set {@code email.sender} (docs/iteracao-4.md, decisão 3 -- no Docker/network there,
 * so there's nothing to publish to). Not a {@code @Profile} switch on purpose: the test
 * classpath's {@code application.yml} replaces (doesn't layer on top of) the main one, so a
 * profile-based default set only in the latter would silently stop applying to every
 * {@code @SpringBootTest}/Cucumber test in the project. {@link SqsEmailSender} takes over
 * wherever {@code email.sender: sqs} is set explicitly.
 */
@Service
@ConditionalOnProperty(name = "email.sender", havingValue = "stub", matchIfMissing = true)
public class StubEmailSender implements EmailSender {

    private final SentEmailRecorder sentEmailRecorder;

    public StubEmailSender(SentEmailRecorder sentEmailRecorder) {
        this.sentEmailRecorder = sentEmailRecorder;
    }

    @Override
    public void send(EmailRequest request) {
        sentEmailRecorder.record(request);
    }
}
