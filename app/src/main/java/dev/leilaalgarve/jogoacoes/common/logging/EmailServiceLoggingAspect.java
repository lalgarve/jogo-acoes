package dev.leilaalgarve.jogoacoes.common.logging;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Spec 05-011, moved by spec 05-034: logs, at DEBUG, every e-mail app asks email-service to send
 * -- {@code EmailServiceGateway.sendEmail}, the one call that leaves app with an e-mail, as
 * {@code SqsEmailSender.send} was while app published to the queue itself. Logs the template,
 * the recipient and the id email-service gave the send, never the template data (it carries the
 * login link) nor the API key. {@code StubEmailSender} never reaches the gateway, so "sent" is
 * still never mixed up with "recorded only".
 */
@Aspect
@Component
public class EmailServiceLoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(EmailServiceLoggingAspect.class);

    @Before("execution(* dev.leilaalgarve.jogoacoes.email.client.EmailServiceGateway.sendEmail(..)) "
            + "&& args(templateName, recipientEmail, ..)")
    public void logBeforeSend(String templateName, String recipientEmail) {
        logger.debug("--> email-service send {} to {}", templateName, recipientEmail);
    }

    @AfterReturning(pointcut = "execution(* dev.leilaalgarve.jogoacoes.email.client.EmailServiceGateway.sendEmail(..))",
            returning = "id")
    public void logAfterSend(UUID id) {
        logger.debug("<-- email-service accepted send {}", id);
    }
}
