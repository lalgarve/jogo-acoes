package dev.leilaalgarve.jogoacoes.email;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Doesn't actually send anything -- records the send in {@code sent_email} instead. The
 * default {@link EmailSender}: active wherever {@code email.sender} is {@code stub} or unset --
 * the tests and the Cucumber suites (spec 05-034). Not a {@code @Profile} switch on purpose: the test
 * classpath's {@code application.yml} replaces (doesn't layer on top of) the main one, so a
 * profile-based default set only in the latter would silently stop applying to every
 * {@code @SpringBootTest}/Cucumber test in the project. {@link EmailServiceEmailSender} takes
 * over wherever {@code email.sender: email-service} is set.
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
