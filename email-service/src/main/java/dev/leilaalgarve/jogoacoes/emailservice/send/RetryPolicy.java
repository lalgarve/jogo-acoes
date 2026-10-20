package dev.leilaalgarve.jogoacoes.emailservice.send;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * When a failed send is tried again, and when email-service gives up on it (spec 05-046): waits
 * {@code initial-delay}, doubling per attempt up to {@code max-delay}; gives up once the send is
 * older than {@code give-up-after}, whatever the attempt count -- what matters to whoever asked
 * for the e-mail is how long it has been.
 */
@Component
class RetryPolicy {

    private static final Logger log = LoggerFactory.getLogger(RetryPolicy.class);

    private final Duration initialDelay;
    private final Duration maxDelay;
    private final Duration giveUpAfter;

    RetryPolicy(@Value("${email.publisher.initial-delay:1s}") Duration initialDelay,
                @Value("${email.publisher.max-delay:5m}") Duration maxDelay,
                @Value("${email.publisher.give-up-after:24h}") Duration giveUpAfter) {
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
        this.giveUpAfter = giveUpAfter;
    }

    /** Records the failure, then schedules the send again ({@code retryNow}: right away) or gives up. */
    void onFailure(EmailSend send, DeliveryErrorType type, String message, String templateData,
                   boolean retryNow, Instant now) {
        send.recordFailure(type, message);
        if (type == DeliveryErrorType.SES_REJECTED) {
            giveUp(send, "SES rejected it");
            return;
        }
        if (now.isAfter(send.getCreatedAt().plus(giveUpAfter))) {
            giveUp(send, "not delivered within " + giveUpAfter);
            return;
        }
        send.retryAt(retryNow ? now : now.plus(delayAfter(send.getAttempts())), templateData);
    }

    private Duration delayAfter(int attempts) {
        Duration delay = initialDelay;
        for (int i = 1; i < attempts && delay.compareTo(maxDelay) < 0; i++) {
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(maxDelay) > 0 ? maxDelay : delay;
    }

    private void giveUp(EmailSend send, String reason) {
        send.fail();
        log.error("Gave up on send {} after {} attempt(s), last error {}: {}", send.getId(), send.getAttempts(),
                send.getLastErrorType(), reason);
    }
}
