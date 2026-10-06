package dev.leilaalgarve.jogoacoes.common.logging;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Spec 05-011: logs, at DEBUG, every message sent through the real queue -- {@link
 * dev.leilaalgarve.jogoacoes.email.SqsEmailSender}'s {@code send}, specifically, not the
 * {@code EmailSender} interface: {@code StubEmailSender} also implements it but never touches
 * a queue, and mixing the two in one aspect would conflate "sent" with "queued".
 */
@Aspect
@Component
public class QueueLoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(QueueLoggingAspect.class);

    @Before("execution(* dev.leilaalgarve.jogoacoes.email.SqsEmailSender.send(..)) && args(request)")
    public void logBeforeSend(JoinPoint joinPoint, Object request) {
        if (logger.isDebugEnabled()) {
            logger.debug("--> queue message {}", LogFormatter.summarize(request));
        }
    }
}
