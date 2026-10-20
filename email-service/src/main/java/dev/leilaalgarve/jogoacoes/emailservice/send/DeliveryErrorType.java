package dev.leilaalgarve.jogoacoes.emailservice.send;

/**
 * Why the last attempt of a send failed (spec 05-046). All but {@link #QUEUE_UNAVAILABLE} come from
 * the dead-letter queue: email-lambda classifies what SES answered and puts it in the message's
 * {@code errorType} attribute; a message SQS's redrive moved has no attribute and is
 * {@link #UNKNOWN}.
 */
public enum DeliveryErrorType {
    /** Publishing to the send queue failed (email-service itself). */
    QUEUE_UNAVAILABLE,
    /** SES does not have the template: email-service recreates it from its database and retries. */
    TEMPLATE_MISSING,
    /** SES refused the e-mail for good (address rejected, sending paused...): no retry. */
    SES_REJECTED,
    /** SES unreachable, throttling or failing on its side: retried later. */
    SES_UNAVAILABLE,
    /** No error type: a message SQS moved after too many failed deliveries. Retried later. */
    UNKNOWN;

    /** The {@code errorType} attribute of a dead-letter message; missing or unknown → {@link #UNKNOWN}. */
    public static DeliveryErrorType fromAttribute(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (DeliveryErrorType type : values()) {
            if (type != QUEUE_UNAVAILABLE && type.name().equals(value)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
