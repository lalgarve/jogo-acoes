package dev.leilaalgarve.jogoacoes.common.testsupport;

import dev.leilaalgarve.jogoacoes.email.EmailRequest;
import dev.leilaalgarve.jogoacoes.email.EmailSender;
import dev.leilaalgarve.jogoacoes.email.StubEmailSender;
import dev.leilaalgarve.jogoacoes.email.exception.EmailServiceUnavailableException;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Primary;

/**
 * The Cucumber suite's {@link EmailSender} (spec 05-056): delegates to {@link StubEmailSender}
 * until a scenario switches email-service "off", then fails the way EmailServiceGateway does when
 * email-service can't be reached. The real email-service can't be taken down mid-scenario, and an
 * app pointed at one that is down doesn't even start (EmailTemplateSynchronizer fails on
 * startup). Only CucumberSpringConfiguration imports it -- {@code @TestComponent} keeps it out of
 * component scanning, so no other test context sees it. CommonSteps switches it back on after
 * every scenario.
 */
@TestComponent
@Primary
public class SwitchableEmailSender implements EmailSender {

    private final StubEmailSender stubEmailSender;
    private volatile boolean unavailable;

    public SwitchableEmailSender(StubEmailSender stubEmailSender) {
        this.stubEmailSender = stubEmailSender;
    }

    public void makeUnavailable() {
        unavailable = true;
    }

    public void makeAvailable() {
        unavailable = false;
    }

    @Override
    public void send(EmailRequest request) {
        if (unavailable) {
            throw new EmailServiceUnavailableException("email-service switched off by the scenario", null);
        }
        stubEmailSender.send(request);
    }
}
